package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.vo.admin.AdminForumMemberPreviewVO;
import org.example.forumdemo.service.interfaces.admin.AdminForumMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台·论坛会员预览")
@RestController
@RequestMapping("/admin/content/member")
public class AdminForumMemberController {

    @Autowired
    private AdminForumMemberService adminForumMemberService;

    @Operation(summary = "论坛会员只读预览")
    @GetMapping("/preview")
    public Result<AdminForumMemberPreviewVO> preview(@RequestParam Long userId) {
        return Result.success(adminForumMemberService.previewMember(userId));
    }
}
