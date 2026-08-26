package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.vip.VipCenterVO;
import org.pluchon.forum.entity.vo.vip.VipPurchaseRecordVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaPanelVO;
import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.service.interfaces.vip.VipCenterService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "会员 VIP", description = "会员中心与配额查询")
@RestController
@RequestMapping("/vip")
public class VipController {

    @Autowired
    private VipSubscribeService vipSubscribeService;

    @Autowired
    private VipCenterService vipCenterService;

    @Operation(summary = "会员中心（方案 + 配额）")
    @GetMapping("/center")
    public Result<VipCenterVO> center(HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipCenterService.center(loginUser.getId()));
    }

    @Operation(summary = "刷新配额面板", description = "按当前 PRO/MAX 档位返回本期用量，需手动刷新")
    @GetMapping("/quota")
    public Result<VipQuotaPanelVO> quota(HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipCenterService.quota(loginUser.getId()));
    }

    /** 查询当前用户的会员购买记录 */
    @Operation(summary = "会员购买记录")
    @GetMapping("/purchase-records")
    public Result<PageResult<VipPurchaseRecordVO>> purchaseRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "6") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipCenterService.purchaseRecords(loginUser.getId(), pageNum, pageSize));
    }

    @Operation(summary = "当前 VIP 与积分余额")
    @GetMapping("/status")
    public Result<VipStatusVO> status(HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipSubscribeService.status(loginUser.getId()));
    }
}
