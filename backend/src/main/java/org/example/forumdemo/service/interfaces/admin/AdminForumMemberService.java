package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.vo.admin.AdminForumMemberPreviewVO;

public interface AdminForumMemberService {

    AdminForumMemberPreviewVO previewMember(Long userId);
}
