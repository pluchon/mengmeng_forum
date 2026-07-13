package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.vo.article.QuestionAnswerVO;

// 问答帖状态与最佳答案业务
public interface ArticleQuestionService {

    // 采纳一条有效一级回答
    void acceptAnswer(Long articleId, Long replyId, Long loginUserId);

    // 关闭仍待解决的问题
    void closeQuestion(Long articleId, Long loginUserId);

    // 查询当前最佳答案，不存在时返回空
    QuestionAnswerVO queryAcceptedAnswer(Long articleId, Long loginUserId);

    // 回答删除时恢复问答状态
    void handleDeletedReply(Long articleId, Long replyId);
}
