package org.pluchon.forum.service.impl.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.EmojiItem;
import org.pluchon.forum.entity.db.EmojiShop;
import org.pluchon.forum.entity.db.UserEmoji;
import org.pluchon.forum.entity.vo.shop.UserEmojiPackVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopListItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.EmojiItemMapper;
import org.pluchon.forum.mapper.EmojiShopMapper;
import org.pluchon.forum.mapper.UserEmojiMapper;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.shop.UserEmojiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

// 用户已购表情包服务实现
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

    @Autowired
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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
        // 仅当售价 > 0 时才扣分 + 写流水; price 0 走 免费领取 , 不写积分流水
        int balanceAfter;
        if (price > 0) {
            balanceAfter = pointsService.deductPoints(userId, price, Constant.POINTS_SOURCE_SHOP_PURCHASE,
                    record.getId(), "购买 " + shop.getName() + " -" + price,
                    "shop_purchase:" + userId + ":" + shopId);
        } else {
            balanceAfter = pointsService.getWallet(userId).getBalance();
        }
        emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId)
                .setSql("sales_count = sales_count + 1"));
        TransactionHooks.afterCommit(() -> {
            invalidateShopDetailCache(shopId);
            invalidateShopListCache();
        });
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
                .in(EmojiShop::getId, shopIds)
                .eq(EmojiShop::getStatus, Constant.SHOP_STATUS_ONLINE)
                .ne(EmojiShop::getDeleteState, 1));
        Map<Long, EmojiShop> shopMap = new HashMap<>();
        for (EmojiShop s : shops) shopMap.put(s.getId(), s);

        Map<Long, UserInternalVO> uploaderMap = new HashMap<>();
        for (EmojiShop s : shops) {
            Long uploaderId = s.getUploadUserId();
            if (uploaderId != null && !uploaderMap.containsKey(uploaderId)) {
                uploaderMap.put(uploaderId, userInternalFeignClient.getById(uploaderId));
            }
        }

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
            if (s == null) continue; // 商品被硬删则跳过 理论不会触发
            UserInternalVO uploader = uploaderMap.get(s.getUploadUserId());
            result.add(new UserEmojiPackVO(u.getId(), s.getId(), s.getName(), s.getCoverUrl(),
                    s.getUploadUserId(), uploader == null ? null : uploader.getNickname(),
                    uploader == null ? null : uploader.getAvatarUrl(),
                    u.getPricePaid(), itemMap.getOrDefault(s.getId(), Collections.emptyList()),
                    u.getCreateTime()));
        }
        return result;
    }

    @Override
    public PageResult<EmojiShopListItemVO> queryMyPurchases(Long userId, String keyword,
                                                            Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize == null ? 8 : pageSize);
        String validKeyword = keyword == null ? "" : keyword.trim();
        List<UserEmoji> purchases = userEmojiMapper.selectList(new LambdaQueryWrapper<UserEmoji>()
                .eq(UserEmoji::getUserId, userId)
                .ne(UserEmoji::getDeleteState, 1)
                .orderByDesc(UserEmoji::getCreateTime)
                .orderByDesc(UserEmoji::getId));
        if (purchases.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L, validPageNum, validPageSize, 0L, false);
        }

        List<Long> shopIds = new ArrayList<>(purchases.size());
        for (UserEmoji purchase : purchases) {
            shopIds.add(purchase.getShopId());
        }
        List<EmojiShop> shops = emojiShopMapper.selectList(new LambdaQueryWrapper<EmojiShop>()
                .in(EmojiShop::getId, shopIds)
                .ne(EmojiShop::getStatus, Constant.SHOP_STATUS_DRAFT)
                .ne(EmojiShop::getDeleteState, 1));
        Map<Long, EmojiShop> shopMap = new HashMap<>();
        Set<Long> uploaderIds = new HashSet<>();
        for (EmojiShop shop : shops) {
            if (validKeyword.isEmpty() || (shop.getName() != null && shop.getName().contains(validKeyword))) {
                shopMap.put(shop.getId(), shop);
                if (shop.getUploadUserId() != null) {
                    uploaderIds.add(shop.getUploadUserId());
                }
            }
        }
        Map<Long, UserInternalVO> uploaderMap = new HashMap<>();
        for (Long uploaderId : uploaderIds) {
            uploaderMap.put(uploaderId, userInternalFeignClient.getById(uploaderId));
        }
        List<EmojiShopListItemVO> allRecords = new ArrayList<>();
        for (UserEmoji purchase : purchases) {
            EmojiShop shop = shopMap.get(purchase.getShopId());
            if (shop == null) {
                continue;
            }
            UserInternalVO uploader = uploaderMap.get(shop.getUploadUserId());
            allRecords.add(new EmojiShopListItemVO(shop.getId(), shop.getName(), shop.getCategory(),
                    shop.getCoverUrl(), shop.getPrice(), shop.getSalesCount(), shop.getUploadUserId(),
                    uploader == null ? null : uploader.getNickname(),
                    uploader == null ? null : uploader.getAvatarUrl(), true, shop.getStatus(),
                    shop.getCreateTime()));
        }
        long total = allRecords.size();
        long pages = total == 0 ? 0 : (total + validPageSize - 1) / validPageSize;
        int fromIndex = Math.min((validPageNum - 1) * validPageSize, allRecords.size());
        int toIndex = Math.min(fromIndex + validPageSize, allRecords.size());
        List<EmojiShopListItemVO> records = new ArrayList<>(allRecords.subList(fromIndex, toIndex));
        return new PageResult<>(records, total, validPageNum, validPageSize, pages, toIndex < allRecords.size());
    }

    private void invalidateShopDetailCache(Long shopId) {
        try {
            stringRedisTemplate.delete(Constant.REDIS_KEY_SHOP_DETAIL + shopId);
        } catch (Exception e) {
            log.warn("失效商城详情缓存失败: shopId={}, {}", shopId, e.getMessage());
        }
    }

    private void invalidateShopListCache() {
        try {
            stringRedisTemplate.opsForValue().increment(Constant.REDIS_KEY_SHOP_LIST_VERSION);
        } catch (Exception e) {
            log.warn("失效表情商城列表缓存失败: {}", e.getMessage());
        }
    }

}
