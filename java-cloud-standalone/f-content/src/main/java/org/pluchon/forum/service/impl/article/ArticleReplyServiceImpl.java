package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.enums.ArticleType;
import org.pluchon.forum.common.enums.QuestionStatus;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.AiAuditUtils;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.ArticleReplyLike;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.article.ReplyArticleRequest;
import org.pluchon.forum.entity.vo.article.ArticleReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.mq.ReplyNotifyMqVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleReplyLikeMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.ArticleSubReplyMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.common.utils.UserMuteGuard;
import org.pluchon.forum.common.utils.RequestIpUtils;
import org.pluchon.forum.service.interfaces.article.ArticleReplyMediaService;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.pluchon.forum.service.interfaces.article.ArticleReplyService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.common.IpRegionService;
import org.pluchon.forum.service.interfaces.user.UserService;
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
    private UserService userService;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private IpRegionService ipRegionService;

    @Autowired
    private ArticleReplyMediaService articleReplyMediaService;

    @Autowired
    private ArticleQuestionService articleQuestionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyArticle(ReplyArticleRequest req, Long loginUserId) {
        User loginUser = userService.queryUserByUserId(loginUserId);
        UserMuteGuard.assertCanPost(loginUser);
        Long articleId = req.getArticleId();
        String content = req.getContent();
        String raw = content == null ? "" : content;
        String plain = raw.replaceAll("<[^>]+>", "").trim();
        boolean hasMedia = req.getMediaList() != null && !req.getMediaList().isEmpty();
        if (!StringUtils.hasText(plain) && !hasMedia) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_CONTENT_EMPTY));
        }
        // 一级评论：与楼中楼一致，极短内容不调远程审核；审核服务异常时降级放行
        String violation = null;
        if (plain.length() >= 25) {
            try {
                violation = AiAuditUtils.isTextAllowed(content);
            } catch (ApplicationException ex) {
                log.warn("一级回复文本审核服务不可用，降级放行: {}", ex.getMessage());
            }
        }
        if (violation != null) {
            log.warn("回复内容审核未通过, userId: {}, reason: {}", loginUserId, violation);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION, violation));
        }
        // 校验帖子并复用 article 信息（写消息队列时还需要楼主 ID）
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null || Objects.equals(article.getState(), (byte) 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (ArticleType.isQuestion(article.getArticleType())
                && Objects.equals(article.getQuestionStatus(), QuestionStatus.CLOSED.getCode())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_QUESTION_STATUS_INVALID));
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
    }

    @Override
    public PageResult<ArticleReplyListResponse> queryReplyByArticleIdWithPage(
            Long articleId, Integer pageNum, Integer pageSize, Long loginUserId) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<ArticleReply> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<ArticleReply> result = articleReplyMapper.selectPage(page, new LambdaQueryWrapper<ArticleReply>()
                .eq(ArticleReply::getArticleId, articleId)
                .ne(ArticleReply::getDeleteState, 1).ne(ArticleReply::getState, 1)
                .orderByAsc(ArticleReply::getCreateTime));
        List<ArticleReply> rows = result.getRecords();
        Map<Long, Integer> subCountMap = loadSubReplyCountMap(rows);
        Set<Long> likedReplyIds = loadLikedReplyIds(loginUserId, rows);
        Map<Long, List<org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO>> mediaMap =
                articleReplyMediaService.mapByReplyIds(rows.stream().map(ArticleReply::getId).collect(Collectors.toList()));
        List<ArticleReplyListResponse> records = rows.stream()
                .map(reply -> buildReplyResponse(reply, subCountMap, likedReplyIds, mediaMap))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext());
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

    /** 单条回复 -> 列表项装配；用户已注销时回退为占位昵称 */
    private ArticleReplyListResponse buildReplyResponse(
            ArticleReply reply, Map<Long, Integer> subCountMap, Set<Long> likedReplyIds,
            Map<Long, List<org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO>> mediaMap) {
        UserBriefVO userBriefVO;
        try {
            User user = userService.queryUserByUserId(reply.getPostUserId());
            userBriefVO = new UserBriefVO(user);
        } catch (ApplicationException e) {
            log.warn("回复 {} 的发表者 {} 已不存在", reply.getId(), reply.getPostUserId());
            userBriefVO = new UserBriefVO();
        }
        ArticleReplyListResponse vo = new ArticleReplyListResponse();
        vo.setArticleReply(reply);
        vo.setUser(userBriefVO);
        vo.setSubReplyCount(subCountMap.getOrDefault(reply.getId(), 0));
        vo.setLiked(likedReplyIds.contains(reply.getId()));
        vo.setMediaList(mediaMap.getOrDefault(reply.getId(), List.of()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long replyId, Long loginUserId) {
        ArticleReply reply = articleReplyMapper.selectOne(new LambdaQueryWrapper<ArticleReply>()
                .eq(ArticleReply::getId, replyId)
                .eq(ArticleReply::getDeleteState, (byte) 0)
                .last("FOR UPDATE"));
        if (reply == null || (reply.getState() != null && reply.getState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (reply.getDeleteState() != null && reply.getDeleteState() == 1) {
            return;
        }
        Article article = articleService.selectArticleByArticleId(reply.getArticleId());
        if (!reply.getPostUserId().equals(loginUserId) && !article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        int updated = articleReplyMapper.update(null, new LambdaUpdateWrapper<ArticleReply>()
                .eq(ArticleReply::getId, replyId)
                .eq(ArticleReply::getDeleteState, 0)
                .set(ArticleReply::getDeleteState, (byte) 1));
        if (updated <= 0) {
            ArticleReply latest = articleReplyMapper.selectById(replyId);
            if (latest != null && latest.getDeleteState() != null && latest.getDeleteState() == 1) {
                return;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        articleQuestionService.handleDeletedReply(reply.getArticleId(), replyId);
        articleService.deleteReply(reply.getArticleId());
    }
}
