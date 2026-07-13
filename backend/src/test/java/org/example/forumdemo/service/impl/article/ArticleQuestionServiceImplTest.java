package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ArticleType;
import org.example.forumdemo.common.enums.QuestionStatus;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleReply;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.ArticleReplyLikeMapper;
import org.example.forumdemo.mapper.ArticleReplyMapper;
import org.example.forumdemo.mapper.ArticleSubReplyMapper;
import org.example.forumdemo.service.interfaces.article.ArticleReplyMediaService;
import org.example.forumdemo.service.interfaces.growth.GrowthService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 问答帖状态与最佳答案业务单元测试
@ExtendWith(MockitoExtension.class)
class ArticleQuestionServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "article-question-test"),
                Article.class);
    }

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private ArticleReplyMapper articleReplyMapper;

    @Mock
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Mock
    private ArticleReplyLikeMapper articleReplyLikeMapper;

    @Mock
    private ArticleReplyMediaService articleReplyMediaService;

    @Mock
    private UserService userService;

    @Mock
    private GrowthService growthService;

    @InjectMocks
    private ArticleQuestionServiceImpl service;

    @Test
    void questionOwnerCanAcceptValidAnswer() {
        Article article = waitingQuestion(11L, 7L);
        ArticleReply reply = validReply(21L, 11L);
        when(articleMapper.selectOne(any(Wrapper.class))).thenReturn(article);
        when(articleReplyMapper.selectOne(any(Wrapper.class))).thenReturn(reply);
        when(articleMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.acceptAnswer(11L, 21L, 7L);

        verify(growthService).requireFormalUser(7L);
        verify(articleReplyMapper).selectOne(any(Wrapper.class));
        verify(articleMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void nonOwnerCannotAcceptAnswer() {
        Article article = waitingQuestion(11L, 7L);
        when(articleMapper.selectOne(any(Wrapper.class))).thenReturn(article);

        assertThrows(ApplicationException.class, () -> service.acceptAnswer(11L, 21L, 8L));

        verify(articleReplyMapper, never()).selectOne(any(Wrapper.class));
        verify(articleMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void resolvedQuestionCannotBeClosed() {
        Article article = waitingQuestion(11L, 7L);
        article.setQuestionStatus(QuestionStatus.RESOLVED.getCode());
        article.setAcceptedReplyId(21L);
        when(articleMapper.selectOne(any(Wrapper.class))).thenReturn(article);

        assertThrows(ApplicationException.class, () -> service.closeQuestion(11L, 7L));

        verify(articleMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void deletingAcceptedAnswerRequestsQuestionRecovery() {
        when(articleMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.handleDeletedReply(11L, 21L);

        verify(articleMapper).update(any(), any(Wrapper.class));
    }

    private Article waitingQuestion(Long articleId, Long ownerId) {
        Article article = new Article();
        article.setId(articleId);
        article.setUserId(ownerId);
        article.setArticleType(ArticleType.QUESTION.getCode());
        article.setQuestionStatus(QuestionStatus.WAITING.getCode());
        article.setStatus(ArticleStatus.PUBLISHED.getCode());
        article.setState((byte) 0);
        article.setDeleteState((byte) 0);
        return article;
    }

    private ArticleReply validReply(Long replyId, Long articleId) {
        ArticleReply reply = new ArticleReply();
        reply.setId(replyId);
        reply.setArticleId(articleId);
        reply.setState((byte) 0);
        reply.setDeleteState((byte) 0);
        return reply;
    }
}
