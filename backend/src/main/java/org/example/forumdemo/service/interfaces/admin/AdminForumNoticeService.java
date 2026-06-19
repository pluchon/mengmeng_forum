package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.dto.admin.AdminForumNoticeSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminForumNoticeUpdateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetNoticePinTopRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetNoticePublishStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminForumNoticeDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminForumNoticeRowVO;
import org.example.forumdemo.entity.vo.admin.AdminIdNameVO;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

public interface AdminForumNoticeService {

    PageResult<AdminForumNoticeRowVO> pageNotices(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                  Integer noticeKind, Long categoryScope, String title,
                                                  Integer deleteState, String sortBy, String sortOrder);

    AdminForumNoticeDetailVO getDetail(Long id);

    List<AdminIdNameVO> listCategoryOptions();

    void save(AdminForumNoticeSaveRequest body);

    void update(AdminForumNoticeUpdateRequest body);

    void setDeleteState(AdminSetDeleteStateRequest body);

    void setPublishState(AdminSetNoticePublishStateRequest body);

    void setPinTop(AdminSetNoticePinTopRequest body);
}
