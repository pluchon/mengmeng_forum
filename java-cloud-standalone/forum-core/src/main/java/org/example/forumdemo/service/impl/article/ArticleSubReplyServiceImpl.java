package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.common.utils.UserMuteGuard;
import org.example.forumdemo.entity.db.ArticleSubReply;
import org.example.forumdemo.entity.db.ArticleSubReplyLike;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.SubReplyRequest;
import org.example.forumdemo.entity.vo.article.ArticleSubReplyListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleSubReplyLikeMapper;
import org.example.forumdemo.mapper.ArticleSubReplyMapper;
import org.example.forumdemo.common.utils.RequestIpUtils;
import org.example.forumdemo.service.interfaces.common.IpRegionService;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.article.ArticleReplyMediaService;
import org.example.forumdemo.service.interfaces.article.ArticleSubReplyService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private ArticleSubReplyLikeMapper articleSubReplyLikeMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private UserService userService;

    @Autowired
    private IpRegionService ipRegionService;

    @Autowired
    private ArticleReplyMediaService articleReplyMediaService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void subReply(SubReplyRequest req, Long loginUserId) {
        User loginUser = userService.queryUserByUserId(loginUserId);
        UserMuteGuard.assertCanPost(loginUser);
        // 楼中楼：极短内容不走远程审核；审核服务异常时降级放行，避免「一发就失败」
        String raw = req.getContent() == null ? "" : req.getContent();
        String plain = raw.replaceAll("<[^>]+>", "").trim();
        boolean hasMedia = req.getMediaList() != null && !req.getMediaList().isEmpty();
        if (!StringUtils.hasText(plain) && !hasMedia) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_CONTENT_EMPTY));
        }
        String violation = null;
        if (plain.length() >= 25) {
            try {
                violation = AiAuditUtils.isTextAllowed(req.getContent());
            } catch (ApplicationException ex) {
                log.warn("楼中楼文本审核服务不可用，降级放行: {}", ex.getMessage());
            }
        }
        if (violation != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION, violation));
        }
        // 校验所属帖子存在 -> 同时防止越权伪造
        articleService.selectArticleByArticleId(req.getArticleId());
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
    }

    @Override
    public PageResult<ArticleSubReplyListResponse> querySubReplyByReplyId(
            Long replyId, Integer pageNum, Integer pageSize, Long loginUserId) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<ArticleSubReply> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<ArticleSubReply> result = articleSubReplyMapper.selectPage(page, new LambdaQueryWrapper<ArticleSubReply>()
                .eq(ArticleSubReply::getReplyId, replyId).ne(ArticleSubReply::getState, 1)
                .ne(ArticleSubReply::getDeleteState, 1).orderByAsc(ArticleSubReply::getCreateTime));
        List<ArticleSubReply> rows = result.getRecords();
        Set<Long> likedSubIds = loadLikedSubReplyIds(loginUserId, rows);
        Map<Long, List<org.example.forumdemo.entity.vo.article.ArticleReplyMediaVO>> mediaMap =
                articleReplyMediaService.mapBySubReplyIds(rows.stream().map(ArticleSubReply::getId).collect(Collectors.toList()));
        List<ArticleSubReplyListResponse> records = rows.stream()
                .map(sub -> buildSubReplyResponse(sub, likedSubIds, mediaMap))
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

    /** 单条楼中楼 -> 列表项装配，被回复用户已注销时昵称留空 */
    private ArticleSubReplyListResponse buildSubReplyResponse(
            ArticleSubReply sub, Set<Long> likedSubIds,
            Map<Long, List<org.example.forumdemo.entity.vo.article.ArticleReplyMediaVO>> mediaMap) {
        User postUser = userService.queryUserByUserId(sub.getPostUserId());
        String replyUserNickname = "";
        if (sub.getReplyUserId() != null) {
            try {
                User replyUser = userService.getUserInfoById(sub.getReplyUserId());
                if (replyUser != null) {
                    replyUserNickname = replyUser.getNickname();
                }
            } catch (ApplicationException e) {
                log.warn("被回复用户 {} 不存在或已被删除", sub.getReplyUserId());
            }
        }
        ArticleSubReplyListResponse vo = new ArticleSubReplyListResponse();
        vo.setSubReply(sub);
        vo.setPostUser(new UserBriefVO(postUser));
        vo.setReplyUserNickname(replyUserNickname);
        vo.setLiked(likedSubIds.contains(sub.getId()));
        vo.setMediaList(mediaMap.getOrDefault(sub.getId(), List.of()));
        return vo;
    }
}
