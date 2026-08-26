package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.QuestionAnswerVO;

import java.util.Set;

// 问答帖状态与采纳业务 采纳与已解决解耦，可多条采纳
public interface ArticleQuestionService {

    // 采纳一条一级回答或楼中楼 不改变问题解决状态
    void acceptAnswer(Long articleId, Long replyId, Long subReplyId, Long loginUserId);

    // 作者切换已解决 / 未解决
    void setResolved(Long articleId, boolean resolved, Long loginUserId);

    // 查询当前展示用「代表采纳回答」 兼容旧接口
    QuestionAnswerVO queryAcceptedAnswer(Long articleId, Long loginUserId);

    // 查询帖子下已采纳的一级回答 ID 集合
    Set<Long> listAcceptedReplyIds(Long articleId);

    // 查询帖子下已采纳的楼中楼 ID 集合
    Set<Long> listAcceptedSubReplyIds(Long articleId);

    // 一级回答删除时清理采纳记录
    void handleDeletedReply(Long articleId, Long replyId);

    // 楼中楼删除时清理采纳记录
    void handleDeletedSubReply(Long articleId, Long subReplyId);
}
