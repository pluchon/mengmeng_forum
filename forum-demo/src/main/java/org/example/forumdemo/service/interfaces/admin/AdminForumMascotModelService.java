package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.dto.admin.AdminForumMascotModelSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminForumMascotShelfRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminForumMascotModelRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;

public interface AdminForumMascotModelService {

    PageResult<AdminForumMascotModelRowVO> pageModels(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                      String keyword, Integer shelfStatus, Integer deleteState);

    Long save(AdminForumMascotModelSaveRequest body);

    void setShelfStatus(AdminForumMascotShelfRequest body);

    void setDeleteState(AdminSetDeleteStateRequest body);
}
