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
    public void replyArticle(ReplyArticleRequest req, Long loginUserId) {
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
        Set<Long> acceptedReplyIds = articleQuestionService.listAcceptedReplyIds(articleId);
        List<ArticleReplyListResponse> records = rows.stream()
                .map(reply -> buildReplyResponse(reply, subCountMap, likedReplyIds, mediaMap, acceptedReplyIds))
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
        return vo;
    }

}
