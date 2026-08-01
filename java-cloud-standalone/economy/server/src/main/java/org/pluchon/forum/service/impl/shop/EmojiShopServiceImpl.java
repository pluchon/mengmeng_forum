package org.pluchon.forum.service.impl.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.AiAuditUtils;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.EmojiItem;
import org.pluchon.forum.entity.db.EmojiShop;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.db.UserEmoji;
import org.pluchon.forum.entity.dto.shop.CreateEmojiShopRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.shop.EmojiShopDetailVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopListItemVO;
import org.pluchon.forum.mapper.EmojiItemMapper;
import org.pluchon.forum.mapper.EmojiShopMapper;
import org.pluchon.forum.mapper.UserEmojiMapper;
import org.pluchon.forum.service.interfaces.shop.EmojiShopService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

    private static final int DEFAULT_SHOP_PAGE_SIZE = 12;
    private static final int DEFAULT_SHOP_ITEM_PAGE_SIZE = 9;
    private static final TypeReference<PageResult<EmojiShopListItemVO>> SHOP_LIST_PAGE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<PageResult<String>> SHOP_ITEM_PAGE_TYPE = new TypeReference<>() {
    };

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

    @Autowired
    private ObjectMapper objectMapper;

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
        invalidateShopListCacheAfterCommit();
        log.info("创建表情包商品成功: shopId={}, operatorUserId={}, isAdmin={}", shop.getId(), operatorUserId, isAdmin);
        return shop.getId();
    }

    /** 作者上架后自动入库，聊天可直接使用 */
    private void grantAuthorPackQuietly(Long userId, Long shopId) {
        Long owned = userEmojiMapper.selectCount(new LambdaQueryWrapper<UserEmoji>()
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
        if (operator == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NO_PERMISSION));
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        if (shop == null || (shop.getDeleteState() != null && shop.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NOT_EXISTS));
        }
        boolean isAdmin = operator.getIsAdmin() != null && operator.getIsAdmin() == 1;
        boolean isAuthor = shop.getUploadUserId() != null && shop.getUploadUserId().equals(operatorUserId);
        if (!isAdmin && !isAuthor) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NO_PERMISSION));
        }
        emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId).set(EmojiShop::getStatus, status));
        TransactionHooks.afterCommit(() -> {
            invalidateShopDetailCache(shopId);
            invalidateShopListCache();
        });
    }

    @Override
    public PageResult<EmojiShopListItemVO> queryShopList(Long loginUserId, String sort, String keyword,
                                                         Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requestedPageSize = pageSize == null || pageSize < 1 ? DEFAULT_SHOP_PAGE_SIZE : pageSize;
        int validPageSize = PageUtils.getValidPageSize(requestedPageSize);
        String validSort = normalizeSort(sort);
        String validKeyword = keyword == null ? "" : keyword.trim();
        String cacheKey = buildShopListCacheKey(validSort, validKeyword, validPageNum, validPageSize);
        PageResult<EmojiShopListItemVO> publicPage = readShopListCache(cacheKey);
        if (publicPage == null) {
            publicPage = queryPublicShopListWithCacheLock(cacheKey, validSort, validKeyword,
                    validPageNum, validPageSize);
        }
        List<EmojiShopListItemVO> records = applyOwnedState(publicPage.getRecords(), loginUserId);
        return new PageResult<>(records, publicPage.getTotal(), validPageNum, validPageSize,
                publicPage.getPages(), publicPage.getHasNextPage());
    }

    private PageResult<EmojiShopListItemVO> queryPublicShopListWithCacheLock(String cacheKey, String validSort,
                                                                             String validKeyword, int validPageNum,
                                                                             int validPageSize) {
        if (!StringUtils.hasText(cacheKey)) {
            return queryPublicShopList(validSort, validKeyword, validPageNum, validPageSize);
        }
        String lockKey = Constant.REDIS_KEY_SHOP_LIST_LOCK + encodeCachePart(cacheKey);
        Boolean locked = Boolean.FALSE;
        try {
            locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                    Constant.REDIS_TTL_SHOP_LIST_LOCK, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                PageResult<EmojiShopListItemVO> publicPage = queryPublicShopList(validSort, validKeyword,
                        validPageNum, validPageSize);
                writeShopListCache(cacheKey, publicPage);
                return publicPage;
            }
            waitForShopCacheWarmup();
            PageResult<EmojiShopListItemVO> cached = readShopListCache(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("表情商城列表缓存锁异常, 降级直查 DB: {}", e.getMessage());
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                try {
                    stringRedisTemplate.delete(lockKey);
                } catch (Exception e) {
                    log.warn("释放表情商城列表缓存锁失败: {}", e.getMessage());
                }
            }
        }
        PageResult<EmojiShopListItemVO> publicPage = queryPublicShopList(validSort, validKeyword,
                validPageNum, validPageSize);
        writeShopListCache(cacheKey, publicPage);
        return publicPage;
    }

    private PageResult<EmojiShopListItemVO> queryPublicShopList(String validSort, String validKeyword,
                                                                int validPageNum, int validPageSize) {
        LambdaQueryWrapper<EmojiShop> qw = new LambdaQueryWrapper<>();
        qw.eq(EmojiShop::getStatus, Constant.SHOP_STATUS_ONLINE).ne(EmojiShop::getDeleteState, 1);
        if (StringUtils.hasText(validKeyword)) {
            qw.like(EmojiShop::getName, validKeyword);
        }
        applySort(qw, validSort);

        Page<EmojiShop> page = new Page<>(validPageNum, validPageSize);
        Page<EmojiShop> result = emojiShopMapper.selectPage(page, qw);
        List<EmojiShopListItemVO> records = enrichPublicListItems(result.getRecords());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public EmojiShopDetailVO queryShopDetail(Long shopId, Long loginUserId,
                                             Integer itemPageNum, Integer itemPageSize) {
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
        int validItemPageNum = PageUtils.getValidPageNum(itemPageNum);
        int requestedItemPageSize = itemPageSize == null || itemPageSize < 1
                ? DEFAULT_SHOP_ITEM_PAGE_SIZE
                : itemPageSize;
        int validItemPageSize = PageUtils.getValidPageSize(requestedItemPageSize);
        String itemPageCacheKey = buildShopItemPageCacheKey(shopId, validItemPageNum, validItemPageSize);
        PageResult<String> imagePage = readShopItemPageCache(itemPageCacheKey);
        if (imagePage == null) {
            imagePage = queryShopItemPageWithCacheLock(shopId, itemPageCacheKey,
                    validItemPageNum, validItemPageSize);
        }
        List<String> imageUrls = imagePage.getRecords() == null
                ? Collections.emptyList()
                : imagePage.getRecords();
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
                imageUrls, imagePage, owned, shop.getCreateTime());
    }

    /** 把 EmojiShop 列表批量补齐 uploader 昵称; owned 按请求用户另行计算, 不写入公开缓存 */
    private List<EmojiShopListItemVO> enrichPublicListItems(List<EmojiShop> shops) {
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
        List<EmojiShopListItemVO> result = new ArrayList<>(shops.size());
        for (EmojiShop s : shops) {
            result.add(new EmojiShopListItemVO(s.getId(), s.getName(), s.getCoverUrl(), s.getPrice(),
                    s.getSalesCount(), s.getUploadUserId(),
                    s.getUploadUserId() == null ? null : uploaderNames.get(s.getUploadUserId()),
                    false, s.getCreateTime()));
        }
        return result;
    }

    private List<EmojiShopListItemVO> applyOwnedState(List<EmojiShopListItemVO> records, Long loginUserId) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ownedShopIds = new HashSet<>();
        if (loginUserId != null) {
            List<Long> shopIds = new ArrayList<>(records.size());
            for (EmojiShopListItemVO row : records) {
                shopIds.add(row.getId());
            }
            List<UserEmoji> owned = userEmojiMapper.selectList(new LambdaQueryWrapper<UserEmoji>()
                    .eq(UserEmoji::getUserId, loginUserId)
                    .in(UserEmoji::getShopId, shopIds)
                    .ne(UserEmoji::getDeleteState, 1));
            for (UserEmoji u : owned) {
                ownedShopIds.add(u.getShopId());
            }
        }
        List<EmojiShopListItemVO> result = new ArrayList<>(records.size());
        for (EmojiShopListItemVO row : records) {
            boolean authorOwned = loginUserId != null && row.getUploadUserId() != null
                    && loginUserId.equals(row.getUploadUserId());
            boolean owned = ownedShopIds.contains(row.getId()) || authorOwned;
            result.add(new EmojiShopListItemVO(row.getId(), row.getName(), row.getCoverUrl(), row.getPrice(),
                    row.getSalesCount(), row.getUploadUserId(), row.getUploadUserNickname(),
                    owned, row.getCreateTime()));
        }
        return result;
    }

    private boolean isOwned(Long userId, Long shopId) {
        Long cnt = userEmojiMapper.selectCount(new LambdaQueryWrapper<UserEmoji>()
                .eq(UserEmoji::getUserId, userId).eq(UserEmoji::getShopId, shopId)
                .ne(UserEmoji::getDeleteState, 1));
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean ownsShopEmojiUrl(Long userId, Long shopId, String url) {
        if (userId == null || userId <= 0 || shopId == null || shopId <= 0
                || url == null || url.isBlank()) {
            return false;
        }
        if (!isOwned(userId, shopId)) {
            return false;
        }
        if (!url.contains(Constant.OSS_PATH_EMOJI_SHOP)) {
            return false;
        }
        List<EmojiItem> items = emojiItemMapper.selectList(new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getShopId, shopId)
                .ne(EmojiItem::getDeleteState, 1));
        String trimmed = url.trim();
        for (EmojiItem item : items) {
            if (item.getImageUrl() != null && trimmed.equals(item.getImageUrl().trim())) {
                return true;
            }
        }
        return false;
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

    private void applySort(LambdaQueryWrapper<EmojiShop> qw, String sort) {
        switch (sort) {
            case "hot":
                qw.orderByDesc(EmojiShop::getSalesCount).orderByDesc(EmojiShop::getId);
                break;
            case "price_asc":
                qw.orderByAsc(EmojiShop::getPrice).orderByDesc(EmojiShop::getId);
                break;
            case "price_desc":
                qw.orderByDesc(EmojiShop::getPrice).orderByDesc(EmojiShop::getId);
                break;
            case "new":
            default:
                qw.orderByDesc(EmojiShop::getCreateTime).orderByDesc(EmojiShop::getId);
                break;
        }
    }

    /**
     * 校验 URL 必须是 OSS_PATH_EMOJI_SHOP 下的本站资源. 不允许外链 / 其他业务目录.
     * 额外拒绝 ".." / 反斜杠 / 控制字符等可能绕过前缀语义的路径段(防 CDN 端规范化后实际指向别处).
     */
    private void validateShopUrl(String url, String hint) {
        if (!ossConfig.matchesPublicObjectUrl(url, Constant.OSS_PATH_EMOJI_SHOP)) {
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
        if (shopId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(Constant.REDIS_KEY_SHOP_DETAIL + shopId);
            stringRedisTemplate.opsForValue().increment(Constant.REDIS_KEY_SHOP_DETAIL_VERSION + shopId);
        } catch (Exception e) {
            log.warn("失效商城详情缓存失败: shopId={}, {}", shopId, e.getMessage());
        }
    }

    private String buildShopListCacheKey(String sort, String keyword, int pageNum, int pageSize) {
        try {
            String version = stringRedisTemplate.opsForValue().get(Constant.REDIS_KEY_SHOP_LIST_VERSION);
            String safeVersion = StringUtils.hasText(version) ? version : "0";
            return Constant.REDIS_KEY_SHOP_LIST
                    + safeVersion
                    + ":"
                    + sort
                    + ":"
                    + pageNum
                    + ":"
                    + pageSize
                    + ":"
                    + encodeCachePart(keyword);
        } catch (Exception e) {
            log.warn("生成表情商城列表缓存 Key 失败, 降级直查 DB: {}", e.getMessage());
            return null;
        }
    }

    private PageResult<EmojiShopListItemVO> readShopListCache(String cacheKey) {
        if (!StringUtils.hasText(cacheKey)) {
            return null;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cached)) {
                return null;
            }
            return objectMapper.readValue(cached, SHOP_LIST_PAGE_TYPE);
        } catch (Exception e) {
            log.warn("读取表情商城列表缓存失败, cacheKey={}, {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeShopListCache(String cacheKey, PageResult<EmojiShopListItemVO> page) {
        if (!StringUtils.hasText(cacheKey) || page == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(page),
                    Constant.REDIS_TTL_SHOP_LIST, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入表情商城列表缓存失败, cacheKey={}, {}", cacheKey, e.getMessage());
        }
    }

    private String buildShopItemPageCacheKey(Long shopId, int pageNum, int pageSize) {
        try {
            String version = stringRedisTemplate.opsForValue()
                    .get(Constant.REDIS_KEY_SHOP_DETAIL_VERSION + shopId);
            String safeVersion = StringUtils.hasText(version) ? version : "0";
            return Constant.REDIS_KEY_SHOP_DETAIL
                    + shopId
                    + ":"
                    + safeVersion
                    + ":items:"
                    + pageNum
                    + ":"
                    + pageSize;
        } catch (Exception e) {
            log.warn("生成表情包详情图片页缓存 Key 失败, 降级直查 DB: {}", e.getMessage());
            return null;
        }
    }

    private PageResult<String> queryShopItemPageWithCacheLock(Long shopId, String cacheKey,
                                                              int pageNum, int pageSize) {
        if (!StringUtils.hasText(cacheKey)) {
            return queryShopItemPage(shopId, pageNum, pageSize);
        }
        String lockKey = Constant.REDIS_KEY_SHOP_DETAIL_LOCK
                + shopId
                + ":"
                + pageNum
                + ":"
                + pageSize;
        Boolean locked = Boolean.FALSE;
        try {
            locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                    Constant.REDIS_TTL_SHOP_DETAIL_LOCK, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                PageResult<String> imagePage = queryShopItemPage(shopId, pageNum, pageSize);
                writeShopItemPageCache(cacheKey, imagePage);
                return imagePage;
            }
            waitForShopCacheWarmup();
            PageResult<String> cached = readShopItemPageCache(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("表情包详情图片页缓存锁异常, 降级直查 DB: shopId={}, {}", shopId, e.getMessage());
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                try {
                    stringRedisTemplate.delete(lockKey);
                } catch (Exception e) {
                    log.warn("释放表情包详情图片页缓存锁失败: shopId={}, {}", shopId, e.getMessage());
                }
            }
        }
        PageResult<String> imagePage = queryShopItemPage(shopId, pageNum, pageSize);
        writeShopItemPageCache(cacheKey, imagePage);
        return imagePage;
    }

    private PageResult<String> queryShopItemPage(Long shopId, int pageNum, int pageSize) {
        Page<EmojiItem> itemPage = new Page<>(pageNum, pageSize);
        Page<EmojiItem> itemResult = emojiItemMapper.selectPage(itemPage, new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getShopId, shopId)
                .ne(EmojiItem::getDeleteState, 1)
                .orderByAsc(EmojiItem::getSort)
                .orderByAsc(EmojiItem::getId));
        List<String> imageUrls = new ArrayList<>(itemResult.getRecords().size());
        for (EmojiItem i : itemResult.getRecords()) {
            imageUrls.add(i.getImageUrl());
        }
        return new PageResult<>(
                imageUrls,
                itemResult.getTotal(),
                pageNum,
                pageSize,
                itemResult.getPages(),
                itemResult.hasNext());
    }

    private PageResult<String> readShopItemPageCache(String cacheKey) {
        if (!StringUtils.hasText(cacheKey)) {
            return null;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cached)) {
                return null;
            }
            return objectMapper.readValue(cached, SHOP_ITEM_PAGE_TYPE);
        } catch (Exception e) {
            log.warn("读取表情包详情图片页缓存失败, cacheKey={}, {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeShopItemPageCache(String cacheKey, PageResult<String> page) {
        if (!StringUtils.hasText(cacheKey) || page == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(page),
                    Constant.REDIS_TTL_SHOP_DETAIL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入表情包详情图片页缓存失败, cacheKey={}, {}", cacheKey, e.getMessage());
        }
    }

    private void invalidateShopListCacheAfterCommit() {
        TransactionHooks.afterCommit(this::invalidateShopListCache);
    }

    private void invalidateShopListCache() {
        try {
            stringRedisTemplate.opsForValue().increment(Constant.REDIS_KEY_SHOP_LIST_VERSION);
        } catch (Exception e) {
            log.warn("失效表情商城列表缓存失败: {}", e.getMessage());
        }
    }

    private String encodeCachePart(String value) {
        if (!StringUtils.hasText(value)) {
            return "none";
        }
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void waitForShopCacheWarmup() {
        try {
            Thread.sleep(80L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
