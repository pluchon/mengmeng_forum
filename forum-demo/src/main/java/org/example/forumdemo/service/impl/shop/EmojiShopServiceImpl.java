package org.example.forumdemo.service.impl.shop;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.OssConfig;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.EmojiItem;
import org.example.forumdemo.entity.db.EmojiShop;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.db.UserEmoji;
import org.example.forumdemo.entity.dto.shop.CreateEmojiShopRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.shop.EmojiShopDetailVO;
import org.example.forumdemo.entity.vo.shop.EmojiShopListItemVO;
import org.example.forumdemo.mapper.EmojiItemMapper;
import org.example.forumdemo.mapper.EmojiShopMapper;
import org.example.forumdemo.mapper.UserEmojiMapper;
import org.example.forumdemo.service.interfaces.shop.EmojiShopService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表情包商城实现.
 * AI 审核:
 *   - 包名: AiAuditUtils.isTextAllowed
 *   - 单图: AiAuditUtils.isImageAllowed 已经在 /file/uploadEmojiShopImage 上传时跑过, 此处不再重复
 *           (URL 校验确保图片来自本站上传通道, 用户无法伪造外链绕过审核)
 * 站长 (isAdmin=1) 创建跳过 AI 审核.
 */
@Service
@Slf4j
public class EmojiShopServiceImpl implements EmojiShopService {

    @Autowired
    private EmojiShopMapper emojiShopMapper;

    @Autowired
    private EmojiItemMapper emojiItemMapper;

    @Autowired
    private UserEmojiMapper userEmojiMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createShop(Long operatorUserId, CreateEmojiShopRequest req) {
        if (operatorUserId == null || operatorUserId <= 0 || req == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User operator = userService.queryUserByUserId(operatorUserId);
        boolean isAdmin = operator != null && operator.getIsAdmin() != null && operator.getIsAdmin() == 1;

        String name = req.getName() == null ? "" : req.getName().trim();
        if (!StringUtils.hasLength(name) || name.length() > 100) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包名称必须 1-100 字"));
        }
        Integer price = req.getPrice();
        if (price == null || price < Constant.EMOJI_SHOP_PRICE_MIN || price > Constant.EMOJI_SHOP_PRICE_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_PRICE_INVALID));
        }
        validateShopUrl(req.getCoverUrl(), "封面图 URL 非法");
        List<String> imageUrls = req.getImageUrls() == null ? Collections.emptyList()
                : new ArrayList<>(new LinkedHashSet<>(req.getImageUrls()));
        if (imageUrls.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_ITEMS_EMPTY));
        }
        if (imageUrls.size() > Constant.EMOJI_SHOP_ITEM_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_ITEMS_LIMIT));
        }
        for (String url : imageUrls) {
            validateShopUrl(url, "包内图片 URL 非法");
        }
        String description = req.getDescription() == null ? "" : req.getDescription().trim();
        if (description.length() > 100) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包说明最多 100 字"));
        }
        // 用户上传: 包名/说明走 AI 文本审核; 站长跳过(站长承担管理责任)
        if (!isAdmin) {
            String reject = AiAuditUtils.isTextAllowed(name);
            if (reject != null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION));
            }
            if (StringUtils.hasLength(description)) {
                reject = AiAuditUtils.isTextAllowed(description);
                if (reject != null) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION));
                }
            }
        }

        EmojiShop shop = new EmojiShop();
        shop.setName(name);
        shop.setDescription(StringUtils.hasLength(description) ? description : null);
        shop.setCoverUrl(req.getCoverUrl().trim());
        shop.setPrice(price);
        shop.setUploadUserId(isAdmin ? null : operatorUserId);
        shop.setSalesCount(0);
        shop.setStatus(Constant.SHOP_STATUS_ONLINE);
        emojiShopMapper.insert(shop);

        int sort = 0;
        for (String url : imageUrls) {
            EmojiItem item = new EmojiItem();
            item.setShopId(shop.getId());
            item.setImageUrl(url.trim());
            item.setSort(sort++);
            emojiItemMapper.insert(item);
        }
        if (shop.getUploadUserId() != null) {
            grantAuthorPackQuietly(shop.getUploadUserId(), shop.getId());
        }
        log.info("创建表情包商品成功: shopId={}, operatorUserId={}, isAdmin={}", shop.getId(), operatorUserId, isAdmin);
        return shop.getId();
    }

    /** 作者上架后自动入库，聊天可直接使用 */
    private void grantAuthorPackQuietly(Long userId, Long shopId) {
        Long owned = userEmojiMapper.selectCount(new QueryWrapper<UserEmoji>().lambda()
                .eq(UserEmoji::getUserId, userId).eq(UserEmoji::getShopId, shopId)
                .ne(UserEmoji::getDeleteState, 1));
        if (owned != null && owned > 0) {
            return;
        }
        UserEmoji record = new UserEmoji();
        record.setUserId(userId);
        record.setShopId(shopId);
        record.setPricePaid(0);
        try {
            userEmojiMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            // 并发重复忽略
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long operatorUserId, Long shopId, Byte status) {
        if (operatorUserId == null || shopId == null || status == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!Constant.SHOP_STATUS_ONLINE.equals(status) && !Constant.SHOP_STATUS_OFFLINE.equals(status)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "status 仅允许 1 / 2"));
        }
        User operator = userService.queryUserByUserId(operatorUserId);
        if (operator == null || operator.getIsAdmin() == null || operator.getIsAdmin() != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NO_PERMISSION));
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        if (shop == null || (shop.getDeleteState() != null && shop.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NOT_EXISTS));
        }
        emojiShopMapper.update(null, new UpdateWrapper<EmojiShop>().lambda()
                .eq(EmojiShop::getId, shopId).set(EmojiShop::getStatus, status));
        invalidateShopDetailCache(shopId);
    }

    @Override
    public PageResult<EmojiShopListItemVO> queryShopList(Long loginUserId, String sort, String keyword,
                                                         Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        String validSort = normalizeSort(sort);
        QueryWrapper<EmojiShop> qw = new QueryWrapper<>();
        qw.lambda().eq(EmojiShop::getStatus, Constant.SHOP_STATUS_ONLINE).ne(EmojiShop::getDeleteState, 1);
        if (StringUtils.hasText(keyword)) {
            qw.lambda().like(EmojiShop::getName, keyword.trim());
        }
        applySort(qw, validSort);

        Page<EmojiShop> page = new Page<>(validPageNum, validPageSize);
        Page<EmojiShop> result = emojiShopMapper.selectPage(page, qw);
        List<EmojiShopListItemVO> records = enrichListItems(result.getRecords(), loginUserId);
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public EmojiShopDetailVO queryShopDetail(Long shopId, Long loginUserId) {
        if (shopId == null || shopId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        if (shop == null || (shop.getDeleteState() != null && shop.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NOT_EXISTS));
        }
        // 仅上架商品对所有人可见; 非上架仅创建者本人 + 管理员可见
        if (!Constant.SHOP_STATUS_ONLINE.equals(shop.getStatus())) {
            boolean canView = false;
            if (loginUserId != null && loginUserId.equals(shop.getUploadUserId())) {
                canView = true;
            }
            if (!canView && loginUserId != null) {
                User operator = userService.queryUserByUserId(loginUserId);
                canView = operator != null && operator.getIsAdmin() != null && operator.getIsAdmin() == 1;
            }
            if (!canView) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NOT_EXISTS));
            }
        }
        List<EmojiItem> items = emojiItemMapper.selectList(new QueryWrapper<EmojiItem>().lambda()
                .eq(EmojiItem::getShopId, shopId).ne(EmojiItem::getDeleteState, 1)
                .orderByAsc(EmojiItem::getSort).orderByAsc(EmojiItem::getId));
        List<String> imageUrls = new ArrayList<>(items.size());
        for (EmojiItem i : items) imageUrls.add(i.getImageUrl());
        String uploaderName = null;
        String uploaderAvatar = null;
        Byte uploaderVipTier = null;
        Date uploaderVipExpire = null;
        if (shop.getUploadUserId() != null) {
            User uploader = userService.queryUserByUserId(shop.getUploadUserId());
            if (uploader != null) {
                uploaderName = uploader.getNickname();
                uploaderAvatar = uploader.getAvatarUrl();
                uploaderVipTier = uploader.getVipTier();
                uploaderVipExpire = uploader.getVipExpireAt();
            }
        }
        boolean isAuthor = loginUserId != null && shop.getUploadUserId() != null
                && loginUserId.equals(shop.getUploadUserId());
        if (isAuthor) {
            grantAuthorPackQuietly(loginUserId, shopId);
        }
        boolean owned = (loginUserId != null && isOwned(loginUserId, shopId)) || isAuthor;
        if (uploaderName == null && shop.getUploadUserId() != null) {
            uploaderName = "用户" + shop.getUploadUserId();
        }
        return new EmojiShopDetailVO(shop.getId(), shop.getName(), shop.getDescription(), shop.getCoverUrl(),
                shop.getPrice(), shop.getSalesCount(), shop.getStatus(), shop.getUploadUserId(), uploaderName,
                uploaderAvatar, uploaderVipTier, uploaderVipExpire,
                imageUrls, owned, shop.getCreateTime());
    }

    /** 把 EmojiShop 列表批量补齐 uploader 昵称 + 是否已购 */
    private List<EmojiShopListItemVO> enrichListItems(List<EmojiShop> shops, Long loginUserId) {
        if (shops.isEmpty()) return Collections.emptyList();
        // 批量查 uploader 昵称, 避免 N+1
        Set<Long> uploaderIds = new HashSet<>();
        for (EmojiShop s : shops) {
            if (s.getUploadUserId() != null) uploaderIds.add(s.getUploadUserId());
        }
        Map<Long, String> uploaderNames = new HashMap<>();
        if (!uploaderIds.isEmpty()) {
            for (Long uid : uploaderIds) {
                User u = userService.queryUserByUserId(uid);
                if (u != null) uploaderNames.put(uid, u.getNickname());
            }
        }
        // 批量查"已购"
        Set<Long> ownedShopIds = new HashSet<>();
        if (loginUserId != null) {
            List<Long> shopIds = new ArrayList<>(shops.size());
            for (EmojiShop s : shops) shopIds.add(s.getId());
            List<UserEmoji> owned = userEmojiMapper.selectList(new QueryWrapper<UserEmoji>().lambda()
                    .eq(UserEmoji::getUserId, loginUserId).in(UserEmoji::getShopId, shopIds)
                    .ne(UserEmoji::getDeleteState, 1));
            for (UserEmoji u : owned) ownedShopIds.add(u.getShopId());
        }
        List<EmojiShopListItemVO> result = new ArrayList<>(shops.size());
        for (EmojiShop s : shops) {
            boolean authorOwned = loginUserId != null && s.getUploadUserId() != null
                    && loginUserId.equals(s.getUploadUserId());
            boolean owned = ownedShopIds.contains(s.getId()) || authorOwned;
            result.add(new EmojiShopListItemVO(s.getId(), s.getName(), s.getCoverUrl(), s.getPrice(),
                    s.getSalesCount(), s.getUploadUserId(),
                    s.getUploadUserId() == null ? null : uploaderNames.get(s.getUploadUserId()),
                    owned, s.getCreateTime()));
        }
        return result;
    }

    private boolean isOwned(Long userId, Long shopId) {
        Long cnt = userEmojiMapper.selectCount(new QueryWrapper<UserEmoji>().lambda()
                .eq(UserEmoji::getUserId, userId).eq(UserEmoji::getShopId, shopId)
                .ne(UserEmoji::getDeleteState, 1));
        return cnt != null && cnt > 0;
    }

    private String normalizeSort(String sort) {
        if (sort == null) return "new";
        switch (sort.toLowerCase()) {
            case "hot":
            case "new":
            case "price_asc":
            case "price_desc":
                return sort.toLowerCase();
            default:
                return "new";
        }
    }

    private void applySort(QueryWrapper<EmojiShop> qw, String sort) {
        switch (sort) {
            case "hot":
                qw.lambda().orderByDesc(EmojiShop::getSalesCount).orderByDesc(EmojiShop::getId);
                break;
            case "price_asc":
                qw.lambda().orderByAsc(EmojiShop::getPrice).orderByDesc(EmojiShop::getId);
                break;
            case "price_desc":
                qw.lambda().orderByDesc(EmojiShop::getPrice).orderByDesc(EmojiShop::getId);
                break;
            case "new":
            default:
                qw.lambda().orderByDesc(EmojiShop::getCreateTime).orderByDesc(EmojiShop::getId);
                break;
        }
    }

    /**
     * 校验 URL 必须是 OSS_PATH_EMOJI_SHOP 下的本站资源. 不允许外链 / 其他业务目录.
     * 额外拒绝 ".." / 反斜杠 / 控制字符等可能绕过前缀语义的路径段(防 CDN 端规范化后实际指向别处).
     */
    private void validateShopUrl(String url, String hint) {
        String normalized = url == null ? "" : url.trim();
        String urlPrefix = ossConfig.getUrlPrefix() == null ? "" : ossConfig.getUrlPrefix().trim();
        String expected = urlPrefix + Constant.OSS_PATH_EMOJI_SHOP;
        boolean prefixOk = StringUtils.hasLength(normalized) && StringUtils.hasLength(urlPrefix)
                && normalized.startsWith(expected);
        boolean safePath = !normalized.contains("..") && !normalized.contains("\\")
                && !containsControlChar(normalized);
        if (!prefixOk || !safePath) {
            log.warn("商城 URL 校验失败: {}", hint);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

    private boolean containsControlChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) return true;
        }
        return false;
    }

    private void invalidateShopDetailCache(Long shopId) {
        try {
            stringRedisTemplate.delete(Constant.REDIS_KEY_SHOP_DETAIL + shopId);
        } catch (Exception e) {
            log.warn("失效商城详情缓存失败: shopId={}, {}", shopId, e.getMessage());
        }
    }
}
