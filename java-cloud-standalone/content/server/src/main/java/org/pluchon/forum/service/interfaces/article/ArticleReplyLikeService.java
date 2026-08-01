package org.pluchon.forum.service.interfaces.article;

public interface ArticleReplyLikeService {

    void likeReply(Long replyId, Long userId);

    void unlikeReply(Long replyId, Long userId);

    void likeSubReply(Long subReplyId, Long userId);

    void unlikeSubReply(Long subReplyId, Long userId);
}
