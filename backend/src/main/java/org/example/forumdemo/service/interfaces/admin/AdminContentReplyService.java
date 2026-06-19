package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.dto.admin.AdminSetArticleStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminArticleReplyRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;

public interface AdminContentReplyService {

    PageResult<AdminArticleReplyRowVO> pageReplies(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                   Long articleId, String contentKeyword, Integer state,
                                                   Integer deleteState);

    void setDeleteState(AdminSetDeleteStateRequest req);

    void setState(AdminSetArticleStateRequest req);
}
