package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.dto.admin.AdminSetArticleStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminArticlePreviewVO;
import org.example.forumdemo.entity.vo.admin.AdminArticleRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;

public interface AdminContentArticleService {

    PageResult<AdminArticleRowVO> pageArticles(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                               String title, Long boardId, Integer status, Integer state,
                                               Integer deleteState);

    void setDeleteState(AdminSetDeleteStateRequest req);

    void setState(AdminSetArticleStateRequest req);

    /** 管理端只读预览（含禁用/未删帖） */
    AdminArticlePreviewVO previewArticle(Long id);
}
