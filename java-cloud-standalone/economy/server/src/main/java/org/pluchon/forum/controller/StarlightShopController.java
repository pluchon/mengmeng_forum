package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.starlight.StarlightExchangeDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeRecordVO;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeResultVO;
import org.pluchon.forum.entity.vo.starlight.StarlightShopItemVO;
import org.pluchon.forum.entity.vo.starlight.StarlightWalletVO;
import org.pluchon.forum.service.interfaces.starlight.StarlightService;
import org.pluchon.forum.service.interfaces.starlight.StarlightShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "萌星辉商城", description = "商品分页 / 兑换入背包 / 兑换记录 / 余额")
@RestController
@RequestMapping("/starlight")
public class StarlightShopController {

    @Autowired
    private StarlightShopService starlightShopService;

    @Autowired
    private StarlightService starlightService;

    @Operation(summary = "萌星辉余额", description = "需登录")
    @GetMapping("/wallet")
    public Result<StarlightWalletVO> wallet(HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(new StarlightWalletVO(starlightService.getBalance(loginUser.getId())));
    }

    @Operation(summary = "商城商品分页", description = "需登录; category=HOT/LIMITED/COSMETIC/UTILITY; 默认每页 8")
    @GetMapping("/shop/items")
    public Result<PageResult<StarlightShopItemVO>> items(
            @RequestParam(defaultValue = "HOT") String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "8") Integer pageSize) {
        return Result.success(starlightShopService.pageItems(category, pageNum, pageSize));
    }

    @Operation(summary = "兑换商品", description = "扣萌星辉并放入背包，奖励需到背包点「使用」才发放")
    @PostMapping("/shop/exchange")
    public Result<StarlightExchangeResultVO> exchange(@RequestBody StarlightExchangeDTO dto,
                                                      HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(starlightShopService.exchange(loginUser.getId(), dto));
    }

    @Operation(summary = "兑换记录", description = "需登录; 分页的购买流水")
    @GetMapping("/shop/exchanges")
    public Result<PageResult<StarlightExchangeRecordVO>> exchanges(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(starlightShopService.pageExchanges(loginUser.getId(), pageNum, pageSize));
    }
}
