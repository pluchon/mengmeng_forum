package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.dto.article.SubReplyRequest;
import org.pluchon.forum.entity.vo.article.ArticleSubReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;

public interface ArticleSubReplyService {

    /**
     * 发表楼中楼回复
     */
    void subReply(SubReplyRequest request, Long loginUserId);

    /**
     * 根据一级回复ID（楼层ID）分页查询楼中楼列表
     */
    PageResult<ArticleSubReplyListResponse> querySubReplyByReplyId(
            Long replyId, Integer pageNum, Integer pageSize, Long loginUserId);
}
