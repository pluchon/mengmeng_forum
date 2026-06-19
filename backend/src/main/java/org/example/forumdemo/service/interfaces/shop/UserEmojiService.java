package org.example.forumdemo.service.interfaces.shop;

import org.example.forumdemo.entity.vo.shop.UserEmojiPackVO;

import java.util.List;

public interface UserEmojiService {

    /**
     * 购买表情包. 内部走 PointsService.deductPoints + 写 user_emoji + 商品 sales_count 自增.
     * 重复购买直接抛 FAILED_SHOP_ALREADY_PURCHASED.
     * @return 购买后用户剩余积分余额
     */
    int purchase(Long userId, Long shopId);

    /**
     * 我的已购列表(聊天面板"我的已购"选项卡用), 每个包带完整的单图 URL 列表.
     * 未删除的 user_emoji + 仍然存在的 emoji_shop(已下架仍展示, 因为用户已经付费).
     *
     * 备注: 当前版本不开放"在面板里隐藏一个包"的后端接口, 原因是 (user_id, shop_id) 唯一键
     * 会让软删后无法再买; 如有此需求, 前端用 localStorage 做视图层过滤.
     */
    List<UserEmojiPackVO> queryMyPacks(Long userId);
}
