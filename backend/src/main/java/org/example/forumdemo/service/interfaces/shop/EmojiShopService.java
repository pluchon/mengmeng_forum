package org.example.forumdemo.service.interfaces.shop;

import org.example.forumdemo.entity.dto.shop.CreateEmojiShopRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.shop.EmojiShopDetailVO;
import org.example.forumdemo.entity.vo.shop.EmojiShopListItemVO;

public interface EmojiShopService {

    /**
     * 创建表情包商品.
     * - 站长(isAdmin=1): 直接 status=1 上架
     * - 普通用户:        包名 + 单图均走 AI 审核, 通过后 status=1 上架; AI 拒收直接抛错, 不入库
     * @return 商品 ID
     */
    Long createShop(Long operatorUserId, CreateEmojiShopRequest req);

    /**
     * 管理员上下架. status 仅允许 1 (上架) 或 2 (下架). 主动失效相关缓存.
     */
    void updateStatus(Long operatorUserId, Long shopId, Byte status);

    /**
     * 分页查询上架商品列表. sort: hot=按销量, new=按创建时间, price_asc / price_desc.
     */
    PageResult<EmojiShopListItemVO> queryShopList(Long loginUserId, String sort, String keyword,
                                                  Integer pageNum, Integer pageSize);

    /**
     * 查询商品详情. 上架商品所有人可见; 非上架仅创建者本人 + 管理员可见(防止用户访问已下架商品的死链).
     */
    EmojiShopDetailVO queryShopDetail(Long shopId, Long loginUserId, Integer itemPageNum, Integer itemPageSize);
}
