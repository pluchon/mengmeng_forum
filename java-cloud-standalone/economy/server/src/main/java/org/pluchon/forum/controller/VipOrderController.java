package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.vip.VipCreateOrderDTO;
import org.pluchon.forum.entity.dto.vip.VipMockPayDTO;
import org.pluchon.forum.entity.vo.vip.VipOrderVO;
import org.pluchon.forum.service.interfaces.vip.VipOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "会员订单", description = "会员下单、查单与支付回调")
@RestController
@RequestMapping("/vip")
public class VipOrderController {

    @Autowired
    private VipOrderService vipOrderService;

    /** 创建会员订单，金额由服务端定价 */
    @Operation(summary = "创建会员订单")
    @PostMapping("/order/create")
    public Result<VipOrderVO> createOrder(@RequestBody VipCreateOrderDTO dto, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipOrderService.createOrder(loginUser.getId(), dto.getTier(), dto.getPayChannel()));
    }

    /** 查询自己的订单状态，支付后前端轮询它 */
    @Operation(summary = "查询会员订单")
    @GetMapping("/order/query")
    public Result<VipOrderVO> queryOrder(@RequestParam String orderNo, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipOrderService.queryOrder(loginUser.getId(), orderNo));
    }

    /** 本地模拟支付成功，走的是真实回调链路 */
    @Operation(summary = "模拟支付成功")
    @PostMapping("/order/mock-pay")
    public Result<VipOrderVO> mockPay(@RequestBody VipMockPayDTO dto, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(vipOrderService.mockPay(loginUser.getId(), dto.getOrderNo()));
    }

    /** 支付渠道回调，无登录态，靠验签放行 */
    @Operation(summary = "支付渠道回调")
    @PostMapping("/payment/callback/{channel}")
    public String paymentCallback(@PathVariable String channel,
                                  @RequestParam(required = false) java.util.Map<String, String> params,
                                  @RequestBody(required = false) String rawBody) {
        // 应答体形状由渠道决定，不能包成统一的 Result，否则渠道认不出来会一直重推
        return vipOrderService.handleCallback(channel, params, rawBody);
    }
}
