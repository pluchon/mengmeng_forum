package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.ArticleReplyLike;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.dto.article.ReplyArticleRequest;
import org.pluchon.forum.entity.vo.article.ArticleReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.mq.ReplyNotifyMqVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleReplyLikeMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.ArticleSubReplyMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.service.impl.remote.ContentUserMuteGuard;
import org.pluchon.forum.common.utils.RequestIpUtils;
import org.pluchon.forum.service.interfaces.article.ArticleReplyMediaService;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.pluchon.forum.service.interfaces.article.ArticleReplyService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.moderation.ContentModerationTaskService;
import org.pluchon.forum.service.interfaces.common.IpRegionService;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleReplyServiceImpl implements ArticleReplyService {

    // 软删标记
    private static final byte DELETED = 1;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Autowired
    private ArticleReplyLikeMapper articleReplyLikeMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ContentUserLookupService userService;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private IpRegionService ipRegionService;

    @Autowired
    private ArticleReplyMediaService articleReplyMediaService;

    @Autowired
    private ArticleQuestionService articleQuestionService;

    @Autowired
    private ContentModerationTaskService contentModerationTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleReplyListResponse replyArticle(ReplyArticleRequest req, Long loginUserId) {
        UserInternalVO loginUser = userService.queryUserByUserId(loginUserId);
        ContentUserMuteGuard.assertCanPost(loginUser);
        Long articleId = req.getArticleId();
        String content = req.getContent();
        String raw = content == null ? "" : content;
        String plain = raw.replaceAll("<[^>]+>", "").trim();
        boolean hasMedia = req.getMediaList() != null && !req.getMediaList().isEmpty();
        if (!StringUtils.hasText(plain) && !hasMedia) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_CONTENT_EMPTY));
        }
        // 校验帖子并复用 article 信息 写消息队列时还需要楼主 ID
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null || Objects.equals(article.getState(), (byte) 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        ArticleReply newReply = new ArticleReply();
        newReply.setArticleId(articleId);
        newReply.setPostUserId(loginUserId);
        newReply.setContent(raw);
        newReply.setIpRegion(ipRegionService.resolveRegion(RequestIpUtils.resolveClientIp()));
        if (articleReplyMapper.insert(newReply) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        articleReplyMediaService.saveForReply(newReply.getId(), req.getMediaList(), loginUserId);
        // 通知楼主
        String summary = null;
        if (content != null) {
            summary = content.length() > 50 ? content.substring(0, 50) : content;
        }
        String postUsername = userService.getUserInfoById(loginUserId).getUsername();
        String eventId = "reply:" + newReply.getId();
        ReplyNotifyMqVO notifyVo = new ReplyNotifyMqVO(eventId, articleId, loginUserId,
                postUsername, article.getUserId(), summary, System.currentTimeMillis());
        TransactionHooks.afterCommit(() -> forumProducer.sendReplyNotify(notifyVo));
        // 帖子回复数 +1
        articleService.addReply(articleId);
        contentModerationTaskService.scheduleComment((byte) 2, newReply.getId(), raw);
        // 复用列表的组装逻辑，返回的对象与列表项完全同构，前端不用另做拼装
        return buildReplyResponse(
                newReply,
                Map.of(newReply.getId(), 0),
                Set.of(),
                articleReplyMediaService.mapByReplyIds(List.of(newReply.getId())),
                Set.of());
    }

    @Override
    public PageResult<ArticleReplyListResponse> queryReplyByArticleIdWithPage(
            Long articleId, Integer pageNum, Integer pageSize, Long loginUserId) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<ArticleReply> page = PageUtils.getPage(validPageNum, validPageSize);
        // 已删除的楼层只在「还有存活的楼中楼」时保留，前端把它渲染成「该评论已删除」占位。
        // 否则楼主一自删，别人在这层下面的回复就全没了。
        // 违规删除会连楼中楼一起标删，那时这个条件不成立，整层自然消失——正是想要的效果
        Page<ArticleReply> result = articleReplyMapper.selectPage(page, new LambdaQueryWrapper<ArticleReply>()
                .eq(ArticleReply::getArticleId, articleId)
                .ne(ArticleReply::getState, 1)
                .and(w -> w.ne(ArticleReply::getDeleteState, DELETED)
                        .or().inSql(ArticleReply::getId,
                                "SELECT DISTINCT reply_id FROM article_sub_reply"
                                        + " WHERE delete_state <> 1 AND state <> 1"))
                .orderByAsc(ArticleReply::getCreateTime));
        List<ArticleReply> rows = result.getRecords();
        Map<Long, Integer> subCountMap = loadSubReplyCountMap(rows);
        Set<Long> likedReplyIds = loadLikedReplyIds(loginUserId, rows);
        Map<Long, List<org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO>> mediaMap =
                articleReplyMediaService.mapByReplyIds(rows.stream().map(ArticleReply::getId).collect(Collectors.toList()));
        Set<Long> acceptedReplyIds = articleQuestionService.listAcceptedReplyIds(articleId);
        List<ArticleReplyListResponse> records = rows.stream()
                .map(reply -> buildReplyResponse(reply, subCountMap, likedReplyIds, mediaMap, acceptedReplyIds))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnReply(Long replyId, Long loginUserId) {
        if (replyId == null || replyId <= 0 || loginUserId == null || loginUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ArticleReply reply = articleReplyMapper.selectById(replyId);
        // 用户点删除的这一刻，目标可能已经被审核或管理员删掉了。
        // 统一按「内容不存在」返回，不要抛异常，也不要泄露它是被判违规删的
        if (reply == null || DELETED == safeDeleteState(reply.getDeleteState())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (!Objects.equals(reply.getPostUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        // 只删自己这一层，**楼中楼一律保留**——别人的发言不该因为楼主想删就跟着消失。
        // 违规删除走 ContentModerationTaskService.deleteConfirmedViolation，那条路才连坐
        int updated = articleReplyMapper.update(null, new LambdaUpdateWrapper<ArticleReply>()
                .eq(ArticleReply::getId, replyId)
                .ne(ArticleReply::getDeleteState, DELETED)
                .set(ArticleReply::getDeleteState, DELETED));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        articleQuestionService.handleDeletedReply(reply.getArticleId(), replyId);
        articleService.deleteReply(reply.getArticleId());
    }

    private static byte safeDeleteState(Byte value) {
        return value == null ? 0 : value;
    }

    private Map<Long, Integer> loadSubReplyCountMap(List<ArticleReply> rows) {
        Map<Long, Integer> map = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return map;
        }
        List<Long> replyIds = rows.stream().map(ArticleReply::getId).collect(Collectors.toList());
        List<ArticleSubReply> subs = articleSubReplyMapper.selectList(new LambdaQueryWrapper<ArticleSubReply>()
                .in(ArticleSubReply::getReplyId, replyIds)
                .ne(ArticleSubReply::getDeleteState, 1)
                .ne(ArticleSubReply::getState, 1)
                .select(ArticleSubReply::getReplyId));
        for (ArticleSubReply sub : subs) {
            Long rid = sub.getReplyId();
            map.put(rid, map.getOrDefault(rid, 0) + 1);
        }
        return map;
    }

    private Set<Long> loadLikedReplyIds(Long loginUserId, List<ArticleReply> rows) {
        if (loginUserId == null || loginUserId <= 0 || rows == null || rows.isEmpty()) {
            return Set.of();
        }
        List<Long> replyIds = rows.stream().map(ArticleReply::getId).collect(Collectors.toList());
        List<ArticleReplyLike> likes = articleReplyLikeMapper.selectList(new LambdaQueryWrapper<ArticleReplyLike>()
                .eq(ArticleReplyLike::getUserId, loginUserId)
                .in(ArticleReplyLike::getReplyId, replyIds));
        return likes.stream().map(ArticleReplyLike::getReplyId).collect(Collectors.toCollection(HashSet::new));
    }

    // 单条回复 > 列表项装配；用户已注销时回退为占位昵称
    private ArticleReplyListResponse buildReplyResponse(
            ArticleReply reply, Map<Long, Integer> subCountMap, Set<Long> likedReplyIds,
            Map<Long, List<org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO>> mediaMap,
            Set<Long> acceptedReplyIds) {
        UserBriefVO userBriefVO;
        try {
            UserInternalVO user = userService.queryUserByUserId(reply.getPostUserId());
            userBriefVO = org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(user);
        } catch (ApplicationException e) {
            log.warn("回复 {} 的发表者 {} 已不存在", reply.getId(), reply.getPostUserId());
            userBriefVO = new UserBriefVO();
        }
        ArticleReplyListResponse vo = new ArticleReplyListResponse();
        vo.setArticleReply(reply);
        vo.setUser(userBriefVO);
        vo.setSubReplyCount(subCountMap.getOrDefault(reply.getId(), 0));
        vo.setLiked(likedReplyIds.contains(reply.getId()));
        vo.setAccepted(acceptedReplyIds != null && acceptedReplyIds.contains(reply.getId()));
        vo.setMediaList(mediaMap.getOrDefault(reply.getId(), List.of()));
        if (DELETED == safeDeleteState(reply.getDeleteState())) {
            // 占位楼层只保留「这里曾经有一条评论」这件事本身，
            // 正文和附件都不能再发给前端——否则删了等于没删，抓包就能看到
            reply.setContent("");
            vo.setMediaList(List.of());
            vo.setLiked(false);
            vo.setAccepted(false);
        }
        return vo;
    }

}
