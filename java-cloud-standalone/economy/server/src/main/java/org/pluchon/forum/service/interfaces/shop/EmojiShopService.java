package org.pluchon.forum.service.interfaces.shop;

import org.pluchon.forum.entity.dto.shop.CreateEmojiShopRequest;
import org.pluchon.forum.entity.dto.shop.SaveEmojiShopDraftRequest;
import org.pluchon.forum.entity.dto.shop.UpdateEmojiShopRequest;
import org.pluchon.forum.api.economy.ShopEmojiAvailability;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.shop.EmojiShopDraftVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopEditVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopDetailVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopListItemVO;
import org.pluchon.forum.entity.vo.shop.ShopEmojiAvailabilityVO;

public interface EmojiShopService {

    // 创建表情包商品. 站长直接上架; 普通用户先待审核，异步文本审核通过后上架，拒绝则撤回
    Long createShop(Long operatorUserId, CreateEmojiShopRequest req);

    // 保存或更新当前用户自己的表情包草稿
    Long saveDraft(Long operatorUserId, SaveEmojiShopDraftRequest req);

    // 将已保存草稿补全并提交上架
    Long submitDraft(Long operatorUserId, SaveEmojiShopDraftRequest req);

    // 分页查询当前用户自己的表情包草稿
    PageResult<EmojiShopListItemVO> queryMyDrafts(Long operatorUserId, String keyword,
                                                  Integer pageNum, Integer pageSize);

    // 查询当前用户自己的完整草稿内容
    EmojiShopDraftVO queryDraft(Long operatorUserId, Long draftId);

    // 分页查询当前用户全部未删除、非草稿表情包
    PageResult<EmojiShopListItemVO> queryMyPublished(Long operatorUserId, String keyword,
                                                     Integer pageNum, Integer pageSize);

    // 查询作者自己的已发布表情包完整编辑数据
    EmojiShopEditVO queryMyPublishedDetail(Long operatorUserId, Long shopId);

    // 原商品 ID 上保存已发布表情包修改
    void updateMyPublished(Long operatorUserId, Long shopId, UpdateEmojiShopRequest request);

    // 作者重新上架自己的已下架表情包
    void relistMyPublished(Long operatorUserId, Long shopId);

    // 删除作者自己的已发布表情包系列
    void deleteMyPublished(Long operatorUserId, Long shopId);

    // 管理员上下架. status 仅允许 1 上架 或 2 下架 . 主动失效相关缓存.
    void updateStatus(Long operatorUserId, Long shopId, Byte status);

    // 分页查询上架商品列表. 分类、排序和关键词检索均由服务端完成
    PageResult<EmojiShopListItemVO> queryShopList(Long loginUserId, String sort, String category, String keyword,
                                                  Integer pageNum, Integer pageSize);

    // 查询商品详情. 上架商品所有人可见；下架商品允许创建者、管理员及历史购买者只读查看
    EmojiShopDetailVO queryShopDetail(Long shopId, Long loginUserId, Integer itemPageNum, Integer itemPageSize);

    // 跨服务 entitlement：用户已购该商店且 url 属于该店表情条目
    boolean ownsShopEmojiUrl(Long userId, Long shopId, String url);

    // 查询商城表情当前状态，不校验购买权益
    ShopEmojiAvailabilityVO queryEmojiAvailability(Long shopId, String url);

    // 查询商城表情当前状态，并校验发送者权益
    ShopEmojiAvailability checkOwnedEmojiAvailability(Long userId, Long shopId, String url);
}
