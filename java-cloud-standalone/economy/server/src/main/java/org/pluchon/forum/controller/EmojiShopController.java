package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.shop.CreateEmojiShopRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.shop.EmojiShopDetailVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopListItemVO;
import org.pluchon.forum.entity.vo.shop.UserEmojiPackVO;
import org.pluchon.forum.service.interfaces.shop.EmojiShopService;
import org.pluchon.forum.service.interfaces.shop.UserEmojiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "表情包商城", description = "商品上下架 / 购买 / 我的已购")
@RestController
@RequestMapping("/shop")
public class EmojiShopController {

    @Autowired
    private EmojiShopService emojiShopService;

    @Autowired
    private UserEmojiService userEmojiService;

    @Operation(summary = "创建表情包商品",
            description = "用户和站长均可创建; 普通用户需通过 AI 包名审核, 单图审核在 /file/uploadEmojiShopImage 时已完成. " +
                          "图片必须先调 /file/uploadEmojiShopImage 拿到本站 URL")
    @PostMapping("/createShop")
    public Result<Long> createShop(@RequestBody CreateEmojiShopRequest req, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.createShop(loginUser.getId(), req));
    }

    @Operation(summary = "上下架商品(管理员)", description = "status 仅允许 1上架 / 2下架; 非管理员调用返回 1144")
    @PutMapping("/updateStatus")
    public Result<Void> updateStatus(@RequestParam Long shopId, @RequestParam Byte status,
                                     HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        emojiShopService.updateStatus(loginUser.getId(), shopId, status);
        return Result.success();
    }

    @Operation(summary = "商城商品列表(分页)",
            description = "未登录可浏览, 已登录会回填每条 owned 字段. sort: hot 按销量 / new 默认按时间 / price_asc / price_desc")
    @GetMapping("/list")
    public Result<PageResult<EmojiShopListItemVO>> queryShopList(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "12") Integer pageSize,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(emojiShopService.queryShopList(loginUserId, sort, keyword, pageNum, pageSize));
    }

    @Operation(summary = "商品详情", description = "上架商品对所有人可见; 已下架/待审仅作者本人 + 管理员可见")
    @GetMapping("/detail")
    public Result<EmojiShopDetailVO> queryShopDetail(@RequestParam Long shopId,
                                                     @RequestParam(defaultValue = "1") Integer itemPageNum,
                                                     @RequestParam(defaultValue = "9") Integer itemPageSize,
                                                     HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(emojiShopService.queryShopDetail(shopId, loginUserId, itemPageNum, itemPageSize));
    }

    @Operation(summary = "购买表情包",
            description = "余额不足返回 1140; 重复购买返回 1139; 已下架返回 1138. 返回购买后余额, 前端可直接刷新顶部")
    @PostMapping("/purchase")
    public Result<Integer> purchase(@RequestParam Long shopId, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userEmojiService.purchase(loginUser.getId(), shopId));
    }

    @Operation(summary = "我的已购表情包",
            description = "聊天面板「我的已购」选项卡使用, 每个包带完整 imageUrls; 不分页, 已购数量不会很大")
    @GetMapping("/myPacks")
    public Result<List<UserEmojiPackVO>> queryMyPacks(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userEmojiService.queryMyPacks(loginUser.getId()));
    }
}
