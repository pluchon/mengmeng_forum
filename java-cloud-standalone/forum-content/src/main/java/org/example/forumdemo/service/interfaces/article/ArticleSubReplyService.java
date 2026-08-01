package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.dto.article.SubReplyRequest;
import org.example.forumdemo.entity.vo.article.ArticleSubReplyListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;

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
