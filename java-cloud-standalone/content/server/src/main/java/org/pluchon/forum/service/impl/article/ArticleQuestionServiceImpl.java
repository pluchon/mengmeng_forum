package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ArticleType;
import org.pluchon.forum.common.enums.QuestionStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.converter.ArticleQuestionConverter;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.ArticleReplyLike;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO;
import org.pluchon.forum.entity.vo.article.QuestionAnswerVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ArticleReplyLikeMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.ArticleSubReplyMapper;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.pluchon.forum.service.interfaces.article.ArticleReplyMediaService;
import org.pluchon.forum.cloud.feign.ContentGrowthInternalFeignClient;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

// 问答帖状态与最佳答案业务实现
@Service
public class ArticleQuestionServiceImpl implements ArticleQuestionService {

    // 正常业务状态编码
    private static final byte NORMAL_STATE = 0;

    // 帖子数据访问
    @Autowired
    private ArticleMapper articleMapper;

    // 一级回答数据访问
    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    // 楼中楼数据访问
    @Autowired
    private ArticleSubReplyMapper articleSubReplyMapper;

    // 一级回答点赞数据访问
    @Autowired
    private ArticleReplyLikeMapper articleReplyLikeMapper;

    // 一级回答媒体业务
    @Autowired
    private ArticleReplyMediaService articleReplyMediaService;

    // 用户信息业务
    @Autowired
    private UserService userService;

    // 用户成长权限业务
    @Autowired
    private ContentGrowthInternalFeignClient contentGrowthInternalFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptAnswer(Long articleId, Long replyId, Long loginUserId) {
        validatePositiveId(articleId);
        validatePositiveId(replyId);
        contentGrowthInternalFeignClient.requireFormalUser(loginUserId);
        Article article = requireQuestion(articleId);
        requireQuestionOwner(article, loginUserId);
        requirePublished(article);

        if (Objects.equals(article.getQuestionStatus(), QuestionStatus.RESOLVED.getCode())) {
            if (Objects.equals(article.getAcceptedReplyId(), replyId)) {
                return;
            }
            throw failed(ResultCode.FAILED_QUESTION_STATUS_INVALID);
        }
        if (!Objects.equals(article.getQuestionStatus(), QuestionStatus.WAITING.getCode())) {
            throw failed(ResultCode.FAILED_QUESTION_STATUS_INVALID);
        }
        requireValidAnswer(articleId, replyId);

        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getUserId, loginUserId)
                .eq(Article::getArticleType, ArticleType.QUESTION.getCode())
                .eq(Article::getQuestionStatus, QuestionStatus.WAITING.getCode())
                .isNull(Article::getAcceptedReplyId)
                .eq(Article::getDeleteState, NORMAL_STATE)
                .eq(Article::getState, NORMAL_STATE)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getAcceptedReplyId, replyId)
                .set(Article::getQuestionStatus, QuestionStatus.RESOLVED.getCode()));
        if (updated > 0) {
            return;
        }
        Article latest = articleMapper.selectById(articleId);
        if (latest != null
                && Objects.equals(latest.getAcceptedReplyId(), replyId)
                && Objects.equals(latest.getQuestionStatus(), QuestionStatus.RESOLVED.getCode())) {
            return;
        }
        throw failed(ResultCode.FAILED_QUESTION_ACCEPT_CONFLICT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeQuestion(Long articleId, Long loginUserId) {
        validatePositiveId(articleId);
        contentGrowthInternalFeignClient.requireFormalUser(loginUserId);
        Article article = requireQuestion(articleId);
        requireQuestionOwner(article, loginUserId);
        requirePublished(article);
        if (Objects.equals(article.getQuestionStatus(), QuestionStatus.CLOSED.getCode())) {
            return;
        }
        if (!Objects.equals(article.getQuestionStatus(), QuestionStatus.WAITING.getCode())) {
            throw failed(ResultCode.FAILED_QUESTION_STATUS_INVALID);
        }
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getUserId, loginUserId)
                .eq(Article::getArticleType, ArticleType.QUESTION.getCode())
                .eq(Article::getQuestionStatus, QuestionStatus.WAITING.getCode())
                .isNull(Article::getAcceptedReplyId)
                .eq(Article::getDeleteState, NORMAL_STATE)
                .eq(Article::getState, NORMAL_STATE)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getQuestionStatus, QuestionStatus.CLOSED.getCode()));
        if (updated <= 0) {
            throw failed(ResultCode.FAILED_QUESTION_ACCEPT_CONFLICT);
        }
    }

    @Override
    public QuestionAnswerVO queryAcceptedAnswer(Long articleId, Long loginUserId) {
        validatePositiveId(articleId);
        Article article = requireQuestion(articleId);
        if (!ArticleStatus.isPublished(article.getStatus()) && !Objects.equals(article.getUserId(), loginUserId)) {
            throw failed(ResultCode.FAILED_NOT_EXISTS);
        }
        if (!Objects.equals(article.getQuestionStatus(), QuestionStatus.RESOLVED.getCode())
                || article.getAcceptedReplyId() == null) {
            return null;
        }
        ArticleReply reply = articleReplyMapper.selectOne(new LambdaQueryWrapper<ArticleReply>()
                .eq(ArticleReply::getId, article.getAcceptedReplyId())
                .eq(ArticleReply::getArticleId, articleId)
                .eq(ArticleReply::getDeleteState, NORMAL_STATE)
                .eq(ArticleReply::getState, NORMAL_STATE));
        if (reply == null) {
            return null;
        }
        UserBriefVO user = loadAnswerUser(reply.getPostUserId());
        Long subReplyCount = articleSubReplyMapper.selectCount(new LambdaQueryWrapper<ArticleSubReply>()
                .eq(ArticleSubReply::getReplyId, reply.getId())
                .eq(ArticleSubReply::getDeleteState, NORMAL_STATE)
                .eq(ArticleSubReply::getState, NORMAL_STATE));
        boolean liked = isLiked(loginUserId, reply.getId());
        List<ArticleReplyMediaVO> mediaList = articleReplyMediaService
                .mapByReplyIds(List.of(reply.getId()))
                .getOrDefault(reply.getId(), List.of());
        return ArticleQuestionConverter.toAnswerVO(
                reply,
                user,
                subReplyCount == null ? 0 : subReplyCount.intValue(),
                liked,
                mediaList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDeletedReply(Long articleId, Long replyId) {
        if (articleId == null || replyId == null) {
            return;
        }
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getArticleType, ArticleType.QUESTION.getCode())
                .eq(Article::getQuestionStatus, QuestionStatus.RESOLVED.getCode())
                .eq(Article::getAcceptedReplyId, replyId)
                .eq(Article::getDeleteState, NORMAL_STATE)
                .set(Article::getAcceptedReplyId, null)
                .set(Article::getQuestionStatus, QuestionStatus.WAITING.getCode()));
    }

    private Article requireQuestion(Long articleId) {
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getDeleteState, NORMAL_STATE)
                .eq(Article::getState, NORMAL_STATE));
        if (article == null) {
            throw failed(ResultCode.FAILED_NOT_EXISTS);
        }
        if (!ArticleType.isQuestion(article.getArticleType())) {
            throw failed(ResultCode.FAILED_NOT_QUESTION_ARTICLE);
        }
        return article;
    }

    private void requireValidAnswer(Long articleId, Long replyId) {
        ArticleReply reply = articleReplyMapper.selectOne(new LambdaQueryWrapper<ArticleReply>()
                .eq(ArticleReply::getId, replyId)
                .eq(ArticleReply::getDeleteState, NORMAL_STATE)
                .last("FOR UPDATE"));
        if (reply == null
                || !Objects.equals(reply.getArticleId(), articleId)
                || !Objects.equals(reply.getState(), NORMAL_STATE)) {
            throw failed(ResultCode.FAILED_QUESTION_ANSWER_INVALID);
        }
    }

    private UserBriefVO loadAnswerUser(Long userId) {
        try {
            User user = userService.queryUserByUserId(userId);
            return org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(user);
        } catch (ApplicationException exception) {
            return new UserBriefVO();
        }
    }

    private boolean isLiked(Long loginUserId, Long replyId) {
        if (loginUserId == null || loginUserId <= 0) {
            return false;
        }
        Long count = articleReplyLikeMapper.selectCount(new LambdaQueryWrapper<ArticleReplyLike>()
                .eq(ArticleReplyLike::getUserId, loginUserId)
                .eq(ArticleReplyLike::getReplyId, replyId));
        return count != null && count > 0;
    }

    private void requireQuestionOwner(Article article, Long loginUserId) {
        if (!Objects.equals(article.getUserId(), loginUserId)) {
            throw failed(ResultCode.FAILED_UNAUTHORIZED);
        }
    }

    private void requirePublished(Article article) {
        if (!ArticleStatus.isPublished(article.getStatus())) {
            throw failed(ResultCode.FAILED_QUESTION_STATUS_INVALID);
        }
    }

    private void validatePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw failed(ResultCode.FAILED_PARAMS_VALIDATE);
        }
    }

    private ApplicationException failed(ResultCode resultCode) {
        return new ApplicationException(Result.fail(resultCode));
    }
}
