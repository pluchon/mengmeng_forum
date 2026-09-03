package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.service.impl.remote.ContentUserMuteGuard;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.entity.db.ArticleSubReplyLike;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.dto.article.SubReplyRequest;
import org.pluchon.forum.entity.vo.article.ArticleSubReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.ArticleSubReplyLikeMapper;
import org.pluchon.forum.mapper.ArticleSubReplyMapper;
import org.pluchon.forum.common.utils.RequestIpUtils;
import org.pluchon.forum.service.interfaces.common.IpRegionService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.article.ArticleReplyMediaService;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.pluchon.forum.service.interfaces.article.ArticleSubReplyService;
import org.pluchon.forum.service.interfaces.moderation.ContentModerationTaskService;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleSubReplyServiceImpl implements ArticleSubReplyService {

    @Autowired
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ArticleSubReplyLikeMapper articleSubReplyLikeMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ContentUserLookupService userService;

    @Autowired
    private IpRegionService ipRegionService;

    @Autowired
    private ArticleReplyMediaService articleReplyMediaService;

    @Autowired
    private ContentModerationTaskService contentModerationTaskService;

    @Autowired
    private ArticleQuestionService articleQuestionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void subReply(SubReplyRequest req, Long loginUserId) {
        UserInternalVO loginUser = userService.queryUserByUserId(loginUserId);
        ContentUserMuteGuard.assertCanPost(loginUser);
        String raw = req.getContent() == null ? "" : req.getContent();
        String plain = raw.replaceAll("<[^>]+>", "").trim();
        boolean hasMedia = req.getMediaList() != null && !req.getMediaList().isEmpty();
        if (!StringUtils.hasText(plain) && !hasMedia) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_CONTENT_EMPTY));
        }
        // 校验所属帖子存在 > 同时防止越权伪造
        articleService.selectArticleByArticleId(req.getArticleId());
        // 用户从打开输入框到点发送这段时间里，楼层可能已经被作者自删或判违规删掉了。
        // 统一按「内容不存在」拒绝：前端会保留已输入的内容，并且不说明是哪种删除——
        // 别人受了什么处置不该广播给第三方
        ArticleReply parent = articleReplyMapper.selectById(req.getReplyId());
        if (parent == null || (parent.getDeleteState() != null && parent.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        ArticleSubReply subReply = new ArticleSubReply();
        subReply.setArticleId(req.getArticleId());
        subReply.setReplyId(req.getReplyId());
        subReply.setPostUserId(loginUserId);
        subReply.setReplyUserId(req.getReplyUserId());
        subReply.setContent(raw);
        subReply.setIpRegion(ipRegionService.resolveRegion(RequestIpUtils.resolveClientIp()));
        if (articleSubReplyMapper.insert(subReply) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        articleReplyMediaService.saveForSubReply(subReply.getId(), req.getMediaList(), loginUserId);
        // 楼中楼不计入楼层数 reply_count, 而是计入 sub_reply_count; 同时按 W_REPLY 入热帖榜分
        articleService.addSubReply(req.getArticleId());
        contentModerationTaskService.scheduleComment((byte) 3, subReply.getId(), raw);
    }

    @Override
    public PageResult<ArticleSubReplyListResponse> querySubReplyByReplyId(
            Long replyId, Integer pageNum, Integer pageSize, Long loginUserId) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<ArticleSubReply> page = PageUtils.getPage(validPageNum, validPageSize);
        // 楼中楼属于连续对话，固定按发布时间升序，避免新回复跑到原消息上方
        Page<ArticleSubReply> result = articleSubReplyMapper.selectPage(page, new LambdaQueryWrapper<ArticleSubReply>()
                .eq(ArticleSubReply::getReplyId, replyId).ne(ArticleSubReply::getState, 1)
                .orderByAsc(ArticleSubReply::getCreateTime)
                .orderByAsc(ArticleSubReply::getId));
        List<ArticleSubReply> rows = result.getRecords();
        Set<Long> likedSubIds = loadLikedSubReplyIds(loginUserId, rows);
        Map<Long, List<org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO>> mediaMap =
                articleReplyMediaService.mapBySubReplyIds(rows.stream().map(ArticleSubReply::getId).collect(Collectors.toList()));
        Long articleId = rows.isEmpty() ? null : rows.get(0).getArticleId();
        Set<Long> acceptedSubIds = articleId == null
                ? Set.of()
                : articleQuestionService.listAcceptedSubReplyIds(articleId);
        List<ArticleSubReplyListResponse> records = rows.stream()
                .map(sub -> buildSubReplyResponse(sub, likedSubIds, mediaMap, acceptedSubIds))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    private Set<Long> loadLikedSubReplyIds(Long loginUserId, List<ArticleSubReply> rows) {
        if (loginUserId == null || loginUserId <= 0 || rows == null || rows.isEmpty()) {
            return Set.of();
        }
        List<Long> subIds = rows.stream().map(ArticleSubReply::getId).collect(Collectors.toList());
        List<ArticleSubReplyLike> likes = articleSubReplyLikeMapper.selectList(new LambdaQueryWrapper<ArticleSubReplyLike>()
                .eq(ArticleSubReplyLike::getUserId, loginUserId)
                .in(ArticleSubReplyLike::getSubReplyId, subIds));
        return likes.stream().map(ArticleSubReplyLike::getSubReplyId).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnSubReply(Long subReplyId, Long loginUserId) {
        if (subReplyId == null || subReplyId <= 0 || loginUserId == null || loginUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ArticleSubReply sub = articleSubReplyMapper.selectById(subReplyId);
        // 点删除的这一刻目标可能已经被审核或管理员删掉了，统一按「内容不存在」返回
        if (sub == null || (sub.getDeleteState() != null && sub.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (!java.util.Objects.equals(sub.getPostUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        int updated = articleSubReplyMapper.update(null, new LambdaUpdateWrapper<ArticleSubReply>()
                .eq(ArticleSubReply::getId, subReplyId)
                .ne(ArticleSubReply::getDeleteState, 1)
                .set(ArticleSubReply::getDeleteState, 1));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        // 删掉的这条仍以占位形式留在列表里：楼中楼是连续对话，
        // 直接抽走会让「回复了它的那条」变得莫名其妙
        articleQuestionService.handleDeletedSubReply(sub.getArticleId(), subReplyId);
        articleService.deleteSubReply(sub.getArticleId());
    }

    // 单条楼中楼 > 列表项装配，被回复用户已注销时昵称留空
    private ArticleSubReplyListResponse buildSubReplyResponse(
            ArticleSubReply sub, Set<Long> likedSubIds,
            Map<Long, List<org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO>> mediaMap,
            Set<Long> acceptedSubIds) {
        UserInternalVO postUser = userService.queryUserByUserId(sub.getPostUserId());
        String replyUserNickname = "";
        if (sub.getReplyUserId() != null) {
            try {
                UserInternalVO replyUser = userService.getUserInfoById(sub.getReplyUserId());
                if (replyUser != null) {
                    replyUserNickname = replyUser.getNickname();
                }
            } catch (ApplicationException e) {
                log.warn("被回复用户 {} 不存在或已被删除", sub.getReplyUserId());
            }
        }
        ArticleSubReplyListResponse vo = new ArticleSubReplyListResponse();
        boolean violated = sub.getDeleteState() != null && sub.getDeleteState() == 1;
        if (violated) {
            ArticleSubReply stub = new ArticleSubReply();
            stub.setId(sub.getId());
            stub.setArticleId(sub.getArticleId());
            stub.setReplyId(sub.getReplyId());
            stub.setPostUserId(sub.getPostUserId());
            stub.setReplyUserId(sub.getReplyUserId());
            stub.setDeleteState(sub.getDeleteState());
            stub.setCreateTime(sub.getCreateTime());
            vo.setSubReply(stub);
            vo.setViolated(true);
        } else {
            vo.setSubReply(sub);
            vo.setViolated(false);
        }
        vo.setPostUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(postUser));
        vo.setReplyUserNickname(replyUserNickname);
        vo.setLiked(!violated && likedSubIds.contains(sub.getId()));
        vo.setAccepted(!violated && acceptedSubIds != null && acceptedSubIds.contains(sub.getId()));
        vo.setMediaList(violated ? List.of() : mediaMap.getOrDefault(sub.getId(), List.of()));
        return vo;
    }
}
