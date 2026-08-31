package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.bag.BagUseDTO;
import org.pluchon.forum.entity.vo.bag.BagItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.bag.UserBagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "我的背包", description = "兑换与中奖所得的卡片类奖品，由用户择时使用")
@RestController
@RequestMapping("/bag")
public class UserBagController {

    @Autowired
    private UserBagService userBagService;

    @Operation(summary = "背包分页", description = "需登录; useStatus 可选 0 未使用 / 1 已使用 / 2 待发放")
    @GetMapping("/items")
    public Result<PageResult<BagItemVO>> items(
            @RequestParam(required = false) Integer useStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "8") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userBagService.list(loginUser.getId(), useStatus, pageNum, pageSize));
    }

    @Operation(summary = "使用背包物品", description = "把奖励真正发到对应钱包，并把物品标为已使用")
    @PostMapping("/use")
    public Result<BagItemVO> use(@RequestBody BagUseDTO dto, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userBagService.use(loginUser.getId(), dto == null ? null : dto.getBagItemId()));
    }

    @Operation(summary = "背包未使用数量", description = "用于入口红点")
    @GetMapping("/unused-count")
    public Result<Integer> unusedCount(HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userBagService.countUnused(loginUser.getId()));
    }
}
