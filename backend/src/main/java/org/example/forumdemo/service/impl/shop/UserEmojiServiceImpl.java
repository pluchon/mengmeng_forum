package org.example.forumdemo.service.impl.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.EmojiItem;
import org.example.forumdemo.entity.db.EmojiShop;
import org.example.forumdemo.entity.db.UserEmoji;
import org.example.forumdemo.entity.vo.shop.UserEmojiPackVO;
import org.example.forumdemo.mapper.EmojiItemMapper;
import org.example.forumdemo.mapper.EmojiShopMapper;
import org.example.forumdemo.mapper.UserEmojiMapper;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.example.forumdemo.service.interfaces.shop.UserEmojiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户已购表情包实现.
 * 购买流程(@Transactional):
 *   1) 商品存在且 status=1, 且当前用户未购买
 *   2) INSERT user_emoji; uix_user_shop 唯一键兜底极端并发重复购买(DuplicateKeyException 走"已购买"分支)
 *   3) pointsService.deductPoints(...): 原子扣分(余额不足直接抛), 写 points_log
 *   4) emoji_shop.sales_count + 1
 * 任一步失败整个事务回滚, 不会出现"扣分了但没拿到包"或"拿到包但没扣分".
 * 先 INSERT 再扣分的好处: 唯一键失败可立即返回"已购买"错误, 不必再做余额预校验.
 */
@Service
@Slf4j
public class UserEmojiServiceImpl implements UserEmojiService {

    @Autowired
    private EmojiShopMapper emojiShopMapper;

    @Autowired
    private EmojiItemMapper emojiItemMapper;

    @Autowired
    private UserEmojiMapper userEmojiMapper;

    @Autowired
    private PointsService pointsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int purchase(Long userId, Long shopId) {
        if (userId == null || userId <= 0 || shopId == null || shopId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        if (shop == null || (shop.getDeleteState() != null && shop.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NOT_EXISTS));
        }
        if (!Constant.SHOP_STATUS_ONLINE.equals(shop.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NOT_ONLINE));
        }
        Long owned = userEmojiMapper.selectCount(new LambdaQueryWrapper<UserEmoji>()
                .eq(UserEmoji::getUserId, userId).eq(UserEmoji::getShopId, shopId)
                .ne(UserEmoji::getDeleteState, 1));
        if (owned != null && owned > 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_ALREADY_PURCHASED));
        }
        if (shop.getUploadUserId() != null && shop.getUploadUserId().equals(userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_ALREADY_PURCHASED, "您是作者，无需购买"));
        }
        int price = shop.getPrice() == null ? 0 : shop.getPrice();

        UserEmoji record = new UserEmoji();
        record.setUserId(userId);
        record.setShopId(shopId);
        record.setPricePaid(price);
        try {
            userEmojiMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 极端并发重复点击购买, 唯一键兜底
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_ALREADY_PURCHASED));
        }
        // 仅当售价 > 0 时才扣分 + 写流水; price=0 走"免费领取", 不写积分流水
        int balanceAfter;
        if (price > 0) {
            balanceAfter = pointsService.deductPoints(userId, price, Constant.POINTS_SOURCE_SHOP_PURCHASE,
                    record.getId(), "购买 " + shop.getName() + " -" + price);
        } else {
            balanceAfter = pointsService.getWallet(userId).getBalance();
        }
        emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId)
                .setSql("sales_count = sales_count + 1"));
        log.info("购买表情包成功: userId={}, shopId={}, price={}, balanceAfter={}", userId, shopId, price, balanceAfter);
        return balanceAfter;
    }

    @Override
    public List<UserEmojiPackVO> queryMyPacks(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        List<UserEmoji> owns = userEmojiMapper.selectList(new LambdaQueryWrapper<UserEmoji>()
                .eq(UserEmoji::getUserId, userId).ne(UserEmoji::getDeleteState, 1)
                .orderByDesc(UserEmoji::getCreateTime).orderByDesc(UserEmoji::getId));
        if (owns.isEmpty()) return Collections.emptyList();

        List<Long> shopIds = new ArrayList<>(owns.size());
        for (UserEmoji u : owns) shopIds.add(u.getShopId());
        // 一次拉商品 + 一次拉所有图片
        List<EmojiShop> shops = emojiShopMapper.selectList(new LambdaQueryWrapper<EmojiShop>()
                .in(EmojiShop::getId, shopIds).ne(EmojiShop::getDeleteState, 1));
        Map<Long, EmojiShop> shopMap = new HashMap<>();
        for (EmojiShop s : shops) shopMap.put(s.getId(), s);

        List<EmojiItem> items = emojiItemMapper.selectList(new LambdaQueryWrapper<EmojiItem>()
                .in(EmojiItem::getShopId, shopIds).ne(EmojiItem::getDeleteState, 1)
                .orderByAsc(EmojiItem::getShopId).orderByAsc(EmojiItem::getSort).orderByAsc(EmojiItem::getId));
        Map<Long, List<String>> itemMap = new HashMap<>();
        for (EmojiItem i : items) {
            itemMap.computeIfAbsent(i.getShopId(), k -> new ArrayList<>()).add(i.getImageUrl());
        }

        List<UserEmojiPackVO> result = new ArrayList<>(owns.size());
        for (UserEmoji u : owns) {
            EmojiShop s = shopMap.get(u.getShopId());
            if (s == null) continue; // 商品被硬删则跳过(理论不会触发)
            result.add(new UserEmojiPackVO(u.getId(), s.getId(), s.getName(), s.getCoverUrl(),
                    u.getPricePaid(), itemMap.getOrDefault(s.getId(), Collections.emptyList()),
                    u.getCreateTime()));
        }
        return result;
    }

}
