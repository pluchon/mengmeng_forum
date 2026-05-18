package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeCatalogSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeCatalogStatusRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeCatalogDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeCatalogRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeOptionVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminLotteryPrizeCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理后台·抽奖奖品库")
@RestController
@RequestMapping("/admin/content/lottery-prize")
public class AdminLotteryPrizeCatalogController {

    @Autowired
    private AdminLotteryPrizeCatalogService adminLotteryPrizeCatalogService;

    @Operation(summary = "奖品分页列表")
    @GetMapping("/getList")
    public Result<PageResult<AdminLotteryPrizeCatalogRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer prizeType,
            @RequestParam(required = false) Integer catalogStatus,
            @RequestParam(required = false) Integer deleteState) {
        return Result.success(adminLotteryPrizeCatalogService.pagePrizes(page, size, pageNum, pageSize,
                keyword, prizeType, catalogStatus, deleteState));
    }

    @Operation(summary = "奖品详情（含神秘子项）")
    @GetMapping("/detail")
    public Result<AdminLotteryPrizeCatalogDetailVO> detail(@RequestParam Long id) {
        return Result.success(adminLotteryPrizeCatalogService.detail(id));
    }

    @Operation(summary = "上架奖品下拉（活动配置用）")
    @GetMapping("/optionsOnShelf")
    public Result<List<AdminLotteryPrizeOptionVO>> optionsOnShelf() {
        return Result.success(adminLotteryPrizeCatalogService.listOptionsOnShelf());
    }

    @Operation(summary = "保存奖品")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody AdminLotteryPrizeCatalogSaveRequest body) {
        return Result.success(adminLotteryPrizeCatalogService.save(body));
    }

    @Operation(summary = "软删奖品")
    @PostMapping("/setDeleteState")
    public Result<Void> setDeleteState(@RequestBody AdminSetDeleteStateRequest body) {
        adminLotteryPrizeCatalogService.setDeleteState(body);
        return Result.success();
    }

    @Operation(summary = "设置奖品库状态")
    @PostMapping("/setCatalogStatus")
    public Result<Void> setCatalogStatus(@RequestBody AdminLotteryPrizeCatalogStatusRequest body) {
        adminLotteryPrizeCatalogService.setCatalogStatus(body);
        return Result.success();
    }
}
