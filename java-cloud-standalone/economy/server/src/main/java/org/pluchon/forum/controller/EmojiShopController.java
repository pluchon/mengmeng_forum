package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.shop.CreateEmojiShopRequest;
import org.pluchon.forum.entity.dto.shop.SaveEmojiShopDraftRequest;
import org.pluchon.forum.entity.dto.shop.UpdateEmojiShopRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.shop.EmojiShopDraftVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopEditVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopDetailVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopListItemVO;
import org.pluchon.forum.entity.vo.shop.UserEmojiPackVO;
import org.pluchon.forum.entity.vo.shop.ShopEmojiAvailabilityVO;
import org.pluchon.forum.service.interfaces.shop.EmojiShopService;
import org.pluchon.forum.service.interfaces.shop.UserEmojiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
            description = "用户和站长均可创建; 普通用户先入库待审核，包名/说明异步 AI 审核通过后上架; "
                    + "单图审核在 /file/uploadEmojiShopImage 时已完成. "
                    + "图片必须先调 /file/uploadEmojiShopImage 拿到本站 URL")
    @PostMapping("/createShop")
    public Result<Long> createShop(@RequestBody CreateEmojiShopRequest req, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.createShop(loginUser.getId(), req));
    }

    /** 保存表情包草稿 */
    @PostMapping("/draft")
    public Result<Long> saveDraft(@RequestBody SaveEmojiShopDraftRequest req, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.saveDraft(loginUser.getId(), req));
    }

    /** 提交已保存的表情包草稿 */
    @PostMapping("/draft/submit")
    public Result<Long> submitDraft(@RequestBody SaveEmojiShopDraftRequest req, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.submitDraft(loginUser.getId(), req));
    }

    /** 查询我的表情包草稿 */
    @GetMapping("/myDrafts")
    public Result<PageResult<EmojiShopListItemVO>> queryMyDrafts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "8") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.queryMyDrafts(loginUser.getId(), keyword, pageNum, pageSize));
    }

    /** 查询当前用户的表情包草稿详情 */
    @GetMapping("/draft")
    public Result<EmojiShopDraftVO> queryDraft(@RequestParam Long draftId, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.queryDraft(loginUser.getId(), draftId));
    }

    /** 查询当前用户发布的表情包 */
    @GetMapping("/myPublished")
    public Result<PageResult<EmojiShopListItemVO>> queryMyPublished(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "8") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.queryMyPublished(loginUser.getId(), keyword, pageNum, pageSize));
    }

    /** 查询作者自己的已发布表情包编辑数据 */
    @GetMapping("/myPublished/{shopId}")
    public Result<EmojiShopEditVO> queryMyPublishedDetail(@PathVariable Long shopId,
                                                          HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(emojiShopService.queryMyPublishedDetail(loginUser.getId(), shopId));
    }

    /** 保存作者自己的已发布表情包修改 */
    @PutMapping("/myPublished/{shopId}")
    public Result<Void> updateMyPublished(@PathVariable Long shopId,
                                          @RequestBody UpdateEmojiShopRequest body,
                                          HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        emojiShopService.updateMyPublished(loginUser.getId(), shopId, body);
        return Result.success();
    }

    /** 作者重新上架自己的已下架表情包 */
    @PutMapping("/myPublished/{shopId}/relist")
    public Result<Void> relistMyPublished(@PathVariable Long shopId, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        emojiShopService.relistMyPublished(loginUser.getId(), shopId);
        return Result.success();
    }

    /** 删除作者自己的已发布表情包系列 */
    @DeleteMapping("/myPublished/{shopId}")
    public Result<Void> deleteMyPublished(@PathVariable Long shopId, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        emojiShopService.deleteMyPublished(loginUser.getId(), shopId);
        return Result.success();
    }

    @Operation(summary = "上下架商品(管理员)", description = "status 仅允许 1上架 / 2下架; 非管理员调用返回 1144")
    @PutMapping("/updateStatus")
    public Result<Void> updateStatus(@RequestParam Long shopId, @RequestParam Byte status,
                                     HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        emojiShopService.updateStatus(loginUser.getId(), shopId, status);
        return Result.success();
    }

    @Operation(summary = "商城商品列表(分页)",
            description = "未登录可浏览, 已登录会回填每条 owned 字段. 分类由 category 筛选; 排序由 sort 在服务端完成")
    @GetMapping("/list")
    public Result<PageResult<EmojiShopListItemVO>> queryShopList(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "8") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(emojiShopService.queryShopList(loginUserId, sort, category, keyword, pageNum, pageSize));
    }

    @Operation(summary = "商品详情", description = "上架商品对所有人可见; 已下架/待审仅作者本人 + 管理员可见")
    @GetMapping("/detail")
    public Result<EmojiShopDetailVO> queryShopDetail(@RequestParam Long shopId,
                                                     @RequestParam(defaultValue = "1") Integer itemPageNum,
                                                     @RequestParam(defaultValue = "8") Integer itemPageSize,
                                                     HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(emojiShopService.queryShopDetail(shopId, loginUserId, itemPageNum, itemPageSize));
    }

    /** 查询商城表情实时可用性，历史消息允许仅传 URL */
    @GetMapping("/emoji/availability")
    public Result<ShopEmojiAvailabilityVO> queryEmojiAvailability(
            @RequestParam(required = false) Long shopId,
            @RequestParam String url) {
        return Result.success(emojiShopService.queryEmojiAvailability(shopId, url));
    }

    @Operation(summary = "购买表情包",
            description = "余额不足返回 1140; 重复购买返回 1139; 已下架返回 1138. 返回购买后余额, 前端可直接刷新顶部")
    @PostMapping("/purchase")
    public Result<Integer> purchase(@RequestParam Long shopId, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userEmojiService.purchase(loginUser.getId(), shopId));
    }

    @Operation(summary = "我的已购表情包",
            description = "聊天面板「我的已购」选项卡使用，仅返回上架系列和有效图片")
    @GetMapping("/myPacks")
    public Result<List<UserEmojiPackVO>> queryMyPacks(HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userEmojiService.queryMyPacks(loginUser.getId()));
    }

    /** 商城 我的已购 ，保留下架系列并分页 */
    @GetMapping("/myPurchases")
    public Result<PageResult<EmojiShopListItemVO>> queryMyPurchases(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "8") Integer pageSize,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        return Result.success(userEmojiService.queryMyPurchases(loginUser.getId(), keyword, pageNum, pageSize));
    }
}
