package org.example.forumdemo.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.vip.VipSubscribeDTO;
import org.example.forumdemo.entity.vo.vip.VipCenterVO;
import org.example.forumdemo.entity.vo.vip.VipQuotaPanelVO;
import org.example.forumdemo.entity.vo.vip.VipStatusVO;
import org.example.forumdemo.entity.vo.vip.VipSubscribeResultVO;
import org.example.forumdemo.service.interfaces.vip.VipCenterService;
import org.example.forumdemo.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "会员 VIP", description = "积分订阅 PRO / MAX")
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
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipCenterService.center(loginUser.getId()));
    }

    @Operation(summary = "刷新配额面板", description = "按当前 PRO/MAX 档位返回本期用量，需手动刷新")
    @GetMapping("/quota")
    public Result<VipQuotaPanelVO> quota(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipCenterService.quota(loginUser.getId()));
    }

    @Operation(summary = "当前 VIP 与积分余额")
    @GetMapping("/status")
    public Result<VipStatusVO> status(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipSubscribeService.status(loginUser.getId()));
    }

    @Operation(summary = "积分订阅", description = "PRO=900 积分/30 天，MAX=2000 积分/30 天；续费从当前到期日起顺延")
    @PostMapping("/subscribe")
    public Result<VipSubscribeResultVO> subscribe(@RequestBody VipSubscribeDTO dto, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipSubscribeService.subscribe(loginUser.getId(), dto));
    }
}
