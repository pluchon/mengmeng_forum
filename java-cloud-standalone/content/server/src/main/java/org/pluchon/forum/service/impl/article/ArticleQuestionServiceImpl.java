package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ArticleType;
import org.pluchon.forum.common.enums.QuestionStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleQuestionAccept;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.ArticleReplyLike;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ArticleQuestionAcceptMapper;
import org.pluchon.forum.mapper.ArticleReplyLikeMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.ArticleSubReplyMapper;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.pluchon.forum.service.interfaces.article.ArticleReplyMediaService;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 问答帖状态与采纳业务：采纳可多条且不联动已解决；关闭问题已移除
@Service
public class ArticleQuestionServiceImpl implements ArticleQuestionService {

    private static final byte NORMAL_STATE = 0;
    private static final int DELETE_FALSE = 0;
    private static final int DELETE_TRUE = 1;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Autowired
    private ArticleReplyLikeMapper articleReplyLikeMapper;

    @Autowired
    private ArticleQuestionAcceptMapper articleQuestionAcceptMapper;

    @Autowired
    private ArticleReplyMediaService articleReplyMediaService;

    @Autowired
    private ContentUserLookupService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptAnswer(Long articleId, Long replyId, Long subReplyId, Long loginUserId) {
        validatePositiveId(articleId);
        boolean hasReply = replyId != null && replyId > 0;
        boolean hasSub = subReplyId != null && subReplyId > 0;
        if (hasReply == hasSub) {
            throw failed(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        Article article = requireQuestion(articleId);
        requireQuestionOwner(article, loginUserId);
        requirePublished(article);

        if (hasReply) {
            requireValidAnswer(articleId, replyId);
            if (isReplyAccepted(articleId, replyId)) {
                return;
            }
            ArticleQuestionAccept row = new ArticleQuestionAccept();
            row.setArticleId(articleId);
            row.setReplyId(replyId);
            row.setDeleteState(DELETE_FALSE);
            articleQuestionAcceptMapper.insert(row);
            // 兼容旧字段：记录最近一次一级采纳
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .set(Article::getAcceptedReplyId, replyId));
            return;
        }

        requireValidSubAnswer(articleId, subReplyId);
        if (isSubReplyAccepted(articleId, subReplyId)) {
            return;
        }
        ArticleQuestionAccept row = new ArticleQuestionAccept();
        row.setArticleId(articleId);
        row.setSubReplyId(subReplyId);
        row.setDeleteState(DELETE_FALSE);
        articleQuestionAcceptMapper.insert(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setResolved(Long articleId, boolean resolved, Long loginUserId) {
        validatePositiveId(articleId);
        Article article = requireQuestion(articleId);
        requireQuestionOwner(article, loginUserId);
        requirePublished(article);

        byte target = resolved
                ? QuestionStatus.RESOLVED.getCode()
                : QuestionStatus.WAITING.getCode();
        if (Objects.equals(article.getQuestionStatus(), target)) {
            return;
        }
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getUserId, loginUserId)
                .eq(Article::getArticleType, ArticleType.QUESTION.getCode())
                .eq(Article::getDeleteState, NORMAL_STATE)
                .eq(Article::getState, NORMAL_STATE)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getQuestionStatus, target));
        if (updated <= 0) {
            throw failed(ResultCode.FAILED_QUESTION_ACCEPT_CONFLICT);
        }
    }

    @Override
    public Set<Long> listAcceptedReplyIds(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return Collections.emptySet();
        }
        return listActiveAccepts(articleId).stream()
                .map(ArticleQuestionAccept::getReplyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Set<Long> listAcceptedSubReplyIds(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return Collections.emptySet();
        }
        return listActiveAccepts(articleId).stream()
                .map(ArticleQuestionAccept::getSubReplyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDeletedReply(Long articleId, Long replyId) {
        if (articleId == null || replyId == null) {
            return;
        }
        articleQuestionAcceptMapper.update(null, new LambdaUpdateWrapper<ArticleQuestionAccept>()
                .eq(ArticleQuestionAccept::getArticleId, articleId)
                .eq(ArticleQuestionAccept::getReplyId, replyId)
                .eq(ArticleQuestionAccept::getDeleteState, DELETE_FALSE)
                .set(ArticleQuestionAccept::getDeleteState, DELETE_TRUE));
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getArticleType, ArticleType.QUESTION.getCode())
                .eq(Article::getAcceptedReplyId, replyId)
                .eq(Article::getDeleteState, NORMAL_STATE)
                .set(Article::getAcceptedReplyId, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDeletedSubReply(Long articleId, Long subReplyId) {
        if (articleId == null || subReplyId == null) {
            return;
        }
        articleQuestionAcceptMapper.update(null, new LambdaUpdateWrapper<ArticleQuestionAccept>()
                .eq(ArticleQuestionAccept::getArticleId, articleId)
                .eq(ArticleQuestionAccept::getSubReplyId, subReplyId)
                .eq(ArticleQuestionAccept::getDeleteState, DELETE_FALSE)
                .set(ArticleQuestionAccept::getDeleteState, DELETE_TRUE));
    }

    private List<ArticleQuestionAccept> listActiveAccepts(Long articleId) {
        return articleQuestionAcceptMapper.selectList(new LambdaQueryWrapper<ArticleQuestionAccept>()
                .eq(ArticleQuestionAccept::getArticleId, articleId)
                .eq(ArticleQuestionAccept::getDeleteState, DELETE_FALSE));
    }

    private boolean isReplyAccepted(Long articleId, Long replyId) {
        Long count = articleQuestionAcceptMapper.selectCount(new LambdaQueryWrapper<ArticleQuestionAccept>()
                .eq(ArticleQuestionAccept::getArticleId, articleId)
                .eq(ArticleQuestionAccept::getReplyId, replyId)
                .eq(ArticleQuestionAccept::getDeleteState, DELETE_FALSE));
        return count != null && count > 0;
    }

    private boolean isSubReplyAccepted(Long articleId, Long subReplyId) {
        Long count = articleQuestionAcceptMapper.selectCount(new LambdaQueryWrapper<ArticleQuestionAccept>()
                .eq(ArticleQuestionAccept::getArticleId, articleId)
                .eq(ArticleQuestionAccept::getSubReplyId, subReplyId)
                .eq(ArticleQuestionAccept::getDeleteState, DELETE_FALSE));
        return count != null && count > 0;
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
        Article article = articleMapper.selectById(articleId);
        if (article != null && Objects.equals(reply.getPostUserId(), article.getUserId())) {
            throw failed(ResultCode.FAILED_QUESTION_ANSWER_INVALID);
        }
    }

    private void requireValidSubAnswer(Long articleId, Long subReplyId) {
        ArticleSubReply sub = articleSubReplyMapper.selectOne(new LambdaQueryWrapper<ArticleSubReply>()
                .eq(ArticleSubReply::getId, subReplyId)
                .eq(ArticleSubReply::getDeleteState, NORMAL_STATE)
                .last("FOR UPDATE"));
        if (sub == null || !Objects.equals(sub.getArticleId(), articleId) || !Objects.equals(sub.getState(), NORMAL_STATE)) {
            throw failed(ResultCode.FAILED_QUESTION_ANSWER_INVALID);
        }
        Article article = articleMapper.selectById(articleId);
        if (article != null && Objects.equals(sub.getPostUserId(), article.getUserId())) {
            throw failed(ResultCode.FAILED_QUESTION_ANSWER_INVALID);
        }
    }

    private UserBriefVO loadAnswerUser(Long userId) {
        try {
            UserInternalVO user = userService.queryUserByUserId(userId);
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
