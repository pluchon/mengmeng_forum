package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.entity.db.ForumArticleTag;
import org.pluchon.forum.entity.db.ForumArticleTagRequest;
import org.pluchon.forum.mapper.ForumArticleTagMapper;
import org.pluchon.forum.mapper.ForumArticleTagRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 标签申请的落库动作
//
// 单独成一个组件，是为了让每次写入各自成一个短事务：
// 1. 被 AI 审核驳回时也要留档，而驳回是靠抛异常返回的。
//    如果这条记录和主流程在同一个事务里，抛异常会把它一起回滚掉，
//    "留下可追溯的申请记录"就永远不会真的发生。
// 2. 通过时的两次写入必须原子，但不该把前面几次 AI 调用一起圈进事务，
//    那会让数据库连接在模型返回之前一直挂着。
@Component
public class ArticleTagRequestWriter {

    private static final byte NOT_DELETED = 0;
    private static final byte STATUS_APPROVED = 1;
    private static final byte STATUS_REJECTED = 2;

    @Autowired
    private ForumArticleTagMapper tagMapper;

    @Autowired
    private ForumArticleTagRequestMapper requestMapper;

    // 驳回留档，独立事务，调用方随后抛异常也不影响
    @Transactional(rollbackFor = Exception.class)
    public void recordRejected(Long userId, Long boardId, Long categoryId,
                               String proposedName, String auditMessage) {
        ForumArticleTagRequest req = new ForumArticleTagRequest();
        req.setUserId(userId);
        req.setBoardId(boardId);
        req.setCategoryId(categoryId);
        req.setProposedName(proposedName);
        req.setStatus(STATUS_REJECTED);
        req.setAuditMessage(auditMessage);
        req.setDeleteState(NOT_DELETED);
        requestMapper.insert(req);
    }

    // 通过：建标签 + 写申请记录，两步必须同生共死
    @Transactional(rollbackFor = Exception.class)
    public Long persistApproved(Long userId, Long boardId, Long categoryId, ForumArticleTag tag) {
        tagMapper.insert(tag);
        ForumArticleTagRequest req = new ForumArticleTagRequest();
        req.setUserId(userId);
        req.setBoardId(boardId);
        req.setCategoryId(categoryId);
        req.setProposedName(tag.getName());
        req.setStatus(STATUS_APPROVED);
        req.setAuditMessage("AI 审核通过");
        req.setApprovedTagId(tag.getId());
        req.setDeleteState(NOT_DELETED);
        requestMapper.insert(req);
        return tag.getId();
    }
}
