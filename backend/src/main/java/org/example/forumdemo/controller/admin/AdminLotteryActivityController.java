package org.example.forumdemo.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivityMetaUpdateRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivityPhaseRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivitySaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminLotteryActivityDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryActivityRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryDrawUserRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryWinRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminLotteryActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台·抽奖活动")
@RestController
@RequestMapping("/admin/content/lottery-activity")
public class AdminLotteryActivityController {

    @Autowired
    private AdminLotteryActivityService adminLotteryActivityService;

    @Operation(summary = "活动分页列表")
    @GetMapping("/getList")
    public Result<PageResult<AdminLotteryActivityRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer phase,
            @RequestParam(required = false) Integer deleteState,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(adminLotteryActivityService.pageActivities(page, size, pageNum, pageSize,
                title, phase, deleteState, sortBy, sortOrder));
    }

    @Operation(summary = "活动详情（含奖池库存）")
    @GetMapping("/detail")
    public Result<AdminLotteryActivityDetailVO> detail(@RequestParam Long id) {
        return Result.success(adminLotteryActivityService.detail(id));
    }

    @Operation(summary = "中奖记录分页")
    @GetMapping("/wins")
    public Result<PageResult<AdminLotteryWinRowVO>> wins(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Long activityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer prizeType) {
        return Result.success(adminLotteryActivityService.pageWins(page, size, pageNum, pageSize,
                activityId, userId, prizeType));
    }

    @Operation(summary = "活动中过奖用户分页（按最近抽奖时间降序）")
    @GetMapping("/drawUsers")
    public Result<PageResult<AdminLotteryDrawUserRowVO>> drawUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Long activityId) {
        return Result.success(adminLotteryActivityService.pageDrawUsers(page, size, pageNum, pageSize, activityId));
    }

    @Operation(summary = "保存活动及奖品配置")
    @PostMapping("/save")
    public Result<Long> save(HttpServletRequest request, @RequestBody AdminLotteryActivitySaveRequest body) {
        User u = (User) request.getAttribute(Constant.USER_SESSION);
        Long op = u != null ? u.getId() : null;
        return Result.success(adminLotteryActivityService.save(body, op));
    }

    @Operation(summary = "设置活动删除标记")
    @PostMapping("/setDeleteState")
    public Result<Void> setDeleteState(@RequestBody AdminSetDeleteStateRequest body) {
        adminLotteryActivityService.setDeleteState(body);
        return Result.success();
    }

    @Operation(summary = "更新活动基本信息（不含奖池）")
    @PostMapping("/updateMeta")
    public Result<Void> updateMeta(@RequestBody AdminLotteryActivityMetaUpdateRequest body) {
        adminLotteryActivityService.updateMeta(body);
        return Result.success();
    }

    @Operation(summary = "设置活动阶段/开放状态")
    @PostMapping("/patchPhase")
    public Result<Void> patchPhase(@RequestBody AdminLotteryActivityPhaseRequest body) {
        adminLotteryActivityService.patchPhase(body);
        return Result.success();
    }
}
