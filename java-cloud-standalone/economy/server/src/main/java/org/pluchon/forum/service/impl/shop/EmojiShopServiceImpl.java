package org.pluchon.forum.service.impl.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.api.economy.ShopEmojiAvailability;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.service.remote.EconomyAiGatewayService;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.EmojiItem;
import org.pluchon.forum.entity.db.EmojiShop;
import org.pluchon.forum.entity.db.UserEmoji;
import org.pluchon.forum.entity.dto.shop.CreateEmojiShopRequest;
import org.pluchon.forum.entity.dto.shop.SaveEmojiShopDraftRequest;
import org.pluchon.forum.entity.dto.shop.UpdateEmojiShopRequest;
import org.pluchon.forum.entity.dto.RagEmojiIndexDTO;
import org.pluchon.forum.entity.enums.EmojiShopCategory;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.shop.EmojiShopDetailVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopDraftVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopListItemVO;
import org.pluchon.forum.entity.vo.shop.EmojiShopEditVO;
import org.pluchon.forum.entity.vo.shop.ShopEmojiAvailabilityVO;
import org.pluchon.forum.mapper.EmojiItemMapper;
import org.pluchon.forum.mapper.EmojiShopMapper;
import org.pluchon.forum.mapper.UserEmojiMapper;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.service.interfaces.shop.EmojiShopService;
import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

// 表情包商城实现. AI 审核: 普通用户创建先入库待审核，事务提交后异步 validateText 包名/说明，通过后上架；单图已在内容服务上传路径审核. 站长 isAdmin 1 创建跳过文本审核直接上架.
@Service
@Slf4j
public class EmojiShopServiceImpl implements EmojiShopService {

    private static final int DEFAULT_SHOP_PAGE_SIZE = 8;
    private static final int DEFAULT_SHOP_ITEM_PAGE_SIZE = 8;
    private static final String DEFAULT_DRAFT_NAME = "未命名草稿";
    private static final int SEMANTIC_SEARCH_CANDIDATE_LIMIT = 120;
    private static final String CATEGORY_ALL = "ALL";
    private static final int COMPREHENSIVE_RECENCY_DAYS = 30;
    private static final double COMPREHENSIVE_SALES_WEIGHT = 0.60D;
    private static final double COMPREHENSIVE_RECENCY_WEIGHT = 0.30D;
    private static final double COMPREHENSIVE_LOW_PRICE_WEIGHT = 0.10D;
    private static final String SHOP_LIST_CACHE_SCHEMA_VERSION = "v3";
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
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Autowired
    private VipSubscribeService vipSubscribeService;

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EconomyAiGatewayService economyAiGatewayService;

    @Autowired
    @Qualifier("emojiShopTextAuditExecutor")
    private Executor emojiShopTextAuditExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createShop(Long operatorUserId, CreateEmojiShopRequest req) {
        if (operatorUserId == null || operatorUserId <= 0 || req == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        UserInternalVO operator = userInternalFeignClient.getById(operatorUserId);
        boolean isAdmin = operator != null && operator.getIsAdmin() != null && operator.getIsAdmin() == 1;

        String name = req.getName() == null ? "" : req.getName().trim();
        if (!StringUtils.hasLength(name) || name.length() > 20) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包名称必须 1-20 字"));
        }
        EmojiShopCategory category = EmojiShopCategory.fromCode(req.getCategory());
        if (category == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请选择有效的表情包分类"));
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
        if (!StringUtils.hasLength(description) || description.length() > 50) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包说明必须 1-50 字"));
        }
        EmojiShop shop = new EmojiShop();
        shop.setName(name);
        shop.setDescription(description);
        shop.setCategory(category.getCode());
        shop.setCoverUrl(req.getCoverUrl().trim());
        shop.setPrice(price);
        // 无论上传者是否具有管理身份，作品的作者归属都必须保留，才能在上架后自动授予作者
        shop.setUploadUserId(operatorUserId);
        shop.setSalesCount(0);
        // 站长直接上架；普通用户先待审核，事务提交后异步文本审核
        shop.setStatus(isAdmin ? Constant.SHOP_STATUS_ONLINE : Constant.SHOP_STATUS_PENDING);
        emojiShopMapper.insert(shop);
        int sort = 0;
        for (String url : imageUrls) {
            EmojiItem item = new EmojiItem();
            item.setShopId(shop.getId());
            item.setImageUrl(url.trim());
            item.setSort(sort++);
            emojiItemMapper.insert(item);
        }
        if (isAdmin) {
            if (shop.getUploadUserId() != null) {
                grantAuthorPackQuietly(shop.getUploadUserId(), shop.getId());
            }
            TransactionHooks.afterCommit(() -> indexEmojiRagQuietly(shop));
            invalidateShopListCacheAfterCommit();
        } else {
            Long shopId = shop.getId();
            TransactionHooks.afterCommit(() -> emojiShopTextAuditExecutor.execute(() -> processPendingShopTextAudit(shopId)));
        }
        return shop.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(Long operatorUserId, SaveEmojiShopDraftRequest req) {
        if (operatorUserId == null || operatorUserId <= 0 || req == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        EmojiShop shop = req.getDraftId() == null ? null : getOwnDraft(operatorUserId, req.getDraftId());
        DraftPayload payload = normalizeDraftPayload(req);
        if (shop == null) {
            shop = new EmojiShop();
            shop.setName(payload.name);
            shop.setDescription(payload.description);
            shop.setCategory(payload.category);
            shop.setCoverUrl(payload.coverUrl);
            shop.setPrice(payload.price);
            shop.setUploadUserId(operatorUserId);
            shop.setSalesCount(0);
            shop.setStatus(Constant.SHOP_STATUS_DRAFT);
            emojiShopMapper.insert(shop);
        } else {
            emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                    .eq(EmojiShop::getId, shop.getId())
                    .set(EmojiShop::getName, payload.name)
                    .set(EmojiShop::getDescription, payload.description)
                    .set(EmojiShop::getCategory, payload.category)
                    .set(EmojiShop::getCoverUrl, payload.coverUrl)
                    .set(EmojiShop::getPrice, payload.price));
        }
        replaceShopItems(shop.getId(), payload.imageUrls);
        return shop.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitDraft(Long operatorUserId, SaveEmojiShopDraftRequest req) {
        if (operatorUserId == null || operatorUserId <= 0 || req == null || req.getDraftId() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        EmojiShop draft = getOwnDraft(operatorUserId, req.getDraftId());
        Long shopId = createShop(operatorUserId, toCreateRequest(req));
        emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, draft.getId())
                .set(EmojiShop::getDeleteState, (byte) 1));
        TransactionHooks.afterCommit(() -> invalidateShopDetailCache(draft.getId()));
        return shopId;
    }

    @Override
    public PageResult<EmojiShopListItemVO> queryMyDrafts(Long operatorUserId, String keyword,
                                                          Integer pageNum, Integer pageSize) {
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requestedPageSize = pageSize == null || pageSize < 1 ? DEFAULT_SHOP_PAGE_SIZE : pageSize;
        int validPageSize = PageUtils.getValidPageSize(requestedPageSize);
        String validKeyword = keyword == null ? "" : keyword.trim();
        Page<EmojiShop> page = new Page<>(validPageNum, validPageSize);
        LambdaQueryWrapper<EmojiShop> query = new LambdaQueryWrapper<EmojiShop>()
                .eq(EmojiShop::getUploadUserId, operatorUserId)
                .eq(EmojiShop::getStatus, Constant.SHOP_STATUS_DRAFT)
                .ne(EmojiShop::getDeleteState, 1);
        if (StringUtils.hasText(validKeyword)) {
            query.like(EmojiShop::getName, validKeyword);
        }
        query.orderByDesc(EmojiShop::getUpdateTime).orderByDesc(EmojiShop::getId);
        Page<EmojiShop> result = emojiShopMapper.selectPage(page, query);
        List<EmojiShopListItemVO> records = new ArrayList<>(result.getRecords().size());
        for (EmojiShop row : result.getRecords()) {
            records.add(new EmojiShopListItemVO(row.getId(), row.getName(), row.getCategory(), row.getCoverUrl(),
                    row.getPrice(), row.getSalesCount(), row.getUploadUserId(), null, null, false,
                    row.getStatus(), row.getCreateTime()));
        }
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public EmojiShopDraftVO queryDraft(Long operatorUserId, Long draftId) {
        if (operatorUserId == null || operatorUserId <= 0 || draftId == null || draftId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        EmojiShop draft = getOwnDraft(operatorUserId, draftId);
        List<EmojiItem> items = emojiItemMapper.selectList(new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getShopId, draft.getId())
                .ne(EmojiItem::getDeleteState, 1)
                .orderByAsc(EmojiItem::getSort)
                .orderByAsc(EmojiItem::getId));
        List<String> imageUrls = new ArrayList<>(items.size());
        for (EmojiItem item : items) {
            imageUrls.add(item.getImageUrl());
        }
        return new EmojiShopDraftVO(draft.getId(), draft.getName(), draft.getDescription(), draft.getCategory(),
                draft.getCoverUrl(), draft.getPrice(), imageUrls, draft.getUpdateTime());
    }

    @Override
    public PageResult<EmojiShopListItemVO> queryMyPublished(Long operatorUserId, String keyword,
                                                            Integer pageNum, Integer pageSize) {
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requestedPageSize = pageSize == null || pageSize < 1 ? DEFAULT_SHOP_PAGE_SIZE : pageSize;
        int validPageSize = PageUtils.getValidPageSize(requestedPageSize);
        String validKeyword = keyword == null ? "" : keyword.trim();
        Page<EmojiShop> page = new Page<>(validPageNum, validPageSize);
        LambdaQueryWrapper<EmojiShop> query = new LambdaQueryWrapper<EmojiShop>()
                .eq(EmojiShop::getUploadUserId, operatorUserId)
                .ne(EmojiShop::getStatus, Constant.SHOP_STATUS_DRAFT)
                .ne(EmojiShop::getDeleteState, 1);
        if (StringUtils.hasText(validKeyword)) {
            query.like(EmojiShop::getName, validKeyword);
        }
        query.orderByDesc(EmojiShop::getUpdateTime).orderByDesc(EmojiShop::getId);
        Page<EmojiShop> result = emojiShopMapper.selectPage(page, query);
        UserInternalVO author = userInternalFeignClient.getById(operatorUserId);
        List<EmojiShopListItemVO> records = new ArrayList<>(result.getRecords().size());
        for (EmojiShop row : result.getRecords()) {
            records.add(new EmojiShopListItemVO(row.getId(), row.getName(), row.getCategory(), row.getCoverUrl(),
                    row.getPrice(), row.getSalesCount(), row.getUploadUserId(),
                    author == null ? null : author.getNickname(), author == null ? null : author.getAvatarUrl(), true,
                    row.getStatus(), row.getCreateTime()));
        }
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public EmojiShopEditVO queryMyPublishedDetail(Long operatorUserId, Long shopId) {
        EmojiShop shop = getOwnPublished(operatorUserId, shopId);
        List<EmojiItem> items = emojiItemMapper.selectList(new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getShopId, shopId)
                .ne(EmojiItem::getDeleteState, 1)
                .orderByAsc(EmojiItem::getSort)
                .orderByAsc(EmojiItem::getId));
        List<String> imageUrls = new ArrayList<>(items.size());
        for (EmojiItem item : items) {
            imageUrls.add(item.getImageUrl());
        }
        return new EmojiShopEditVO(shop.getId(), shop.getName(), shop.getDescription(), shop.getCategory(),
                shop.getCoverUrl(), shop.getPrice(), shop.getStatus(), imageUrls, shop.getUpdateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMyPublished(Long operatorUserId, Long shopId, UpdateEmojiShopRequest request) {
        EmojiShop shop = getOwnPublished(operatorUserId, shopId);
        if (Constant.SHOP_STATUS_PENDING.equals(shop.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "审核中，暂不可编辑"));
        }
        PublishedPayload payload = normalizePublishedPayload(operatorUserId, request);
        emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId)
                .eq(EmojiShop::getUploadUserId, operatorUserId)
                .ne(EmojiShop::getDeleteState, 1)
                .set(EmojiShop::getName, payload.name)
                .set(EmojiShop::getDescription, payload.description)
                .set(EmojiShop::getCategory, payload.category)
                .set(EmojiShop::getCoverUrl, payload.coverUrl)
                .set(EmojiShop::getPrice, payload.price));
        updateShopItemsByUrl(shopId, payload.imageUrls);

        shop.setName(payload.name);
        shop.setDescription(payload.description);
        shop.setCategory(payload.category);
        shop.setCoverUrl(payload.coverUrl);
        shop.setPrice(payload.price);
        TransactionHooks.afterCommit(() -> {
            invalidateShopDetailCache(shopId);
            invalidateShopListCache();
            if (Constant.SHOP_STATUS_ONLINE.equals(shop.getStatus())) {
                indexEmojiRagQuietly(shop);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void relistMyPublished(Long operatorUserId, Long shopId) {
        EmojiShop shop = getOwnPublished(operatorUserId, shopId);
        if (!Constant.SHOP_STATUS_OFFLINE.equals(shop.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "只有已下架表情包可以重新上架"));
        }
        Long activeItemCount = emojiItemMapper.selectCount(new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getShopId, shopId)
                .ne(EmojiItem::getDeleteState, 1));
        if (activeItemCount == null || activeItemCount <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请至少保留一张有效表情图片"));
        }
        emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId)
                .eq(EmojiShop::getUploadUserId, operatorUserId)
                .eq(EmojiShop::getStatus, Constant.SHOP_STATUS_OFFLINE)
                .ne(EmojiShop::getDeleteState, 1)
                .set(EmojiShop::getStatus, Constant.SHOP_STATUS_ONLINE));
        shop.setStatus(Constant.SHOP_STATUS_ONLINE);
        TransactionHooks.afterCommit(() -> {
            invalidateShopDetailCache(shopId);
            invalidateShopListCache();
            indexEmojiRagQuietly(shop);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyPublished(Long operatorUserId, Long shopId) {
        getOwnPublished(operatorUserId, shopId);
        emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId)
                .eq(EmojiShop::getUploadUserId, operatorUserId)
                .set(EmojiShop::getDeleteState, (byte) 1));
        TransactionHooks.afterCommit(() -> {
            invalidateShopDetailCache(shopId);
            invalidateShopListCache();
        });
    }

    private EmojiShop getOwnPublished(Long operatorUserId, Long shopId) {
        if (operatorUserId == null || operatorUserId <= 0 || shopId == null || shopId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        boolean isOwnPublished = shop != null
                && (shop.getDeleteState() == null || shop.getDeleteState() != 1)
                && !Constant.SHOP_STATUS_DRAFT.equals(shop.getStatus())
                && operatorUserId.equals(shop.getUploadUserId());
        if (!isOwnPublished) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NO_PERMISSION));
        }
        return shop;
    }

    private PublishedPayload normalizePublishedPayload(Long operatorUserId, UpdateEmojiShopRequest request) {
        if (request == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String name = request.getName() == null ? "" : request.getName().trim();
        if (!StringUtils.hasText(name) || name.length() > 20) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包名称必须 1-20 字"));
        }
        EmojiShopCategory category = EmojiShopCategory.fromCode(request.getCategory());
        if (category == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请选择有效的表情包分类"));
        }
        Integer price = request.getPrice();
        if (price == null || price < Constant.EMOJI_SHOP_PRICE_MIN || price > Constant.EMOJI_SHOP_PRICE_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_PRICE_INVALID));
        }
        validateShopUrl(request.getCoverUrl(), "封面图 URL 非法");
        List<String> imageUrls = normalizeShopImageUrls(request.getImageUrls());
        if (imageUrls.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_ITEMS_EMPTY));
        }
        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        if (!StringUtils.hasText(description) || description.length() > 50) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包说明必须 1-50 字"));
        }
        UserInternalVO operator = userInternalFeignClient.getById(operatorUserId);
        boolean isAdmin = operator != null && operator.getIsAdmin() != null && operator.getIsAdmin() == 1;
        if (!isAdmin) {
            if (economyAiGatewayService.validateText(name) != null
                    || economyAiGatewayService.validateText(description) != null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION));
            }
        }
        return new PublishedPayload(name, description,
                category.getCode(), request.getCoverUrl().trim(), price, imageUrls);
    }

    private void updateShopItemsByUrl(Long shopId, List<String> imageUrls) {
        List<EmojiItem> existingItems = emojiItemMapper.selectList(new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getShopId, shopId)
                .orderByAsc(EmojiItem::getId));
        Map<String, EmojiItem> existingByUrl = new HashMap<>();
        for (EmojiItem item : existingItems) {
            if (item.getImageUrl() != null) {
                existingByUrl.putIfAbsent(item.getImageUrl().trim(), item);
            }
        }
        Set<String> retainedUrls = new HashSet<>(imageUrls);
        for (EmojiItem item : existingItems) {
            if ((item.getDeleteState() == null || item.getDeleteState() != 1)
                    && !retainedUrls.contains(item.getImageUrl().trim())) {
                emojiItemMapper.update(null, new LambdaUpdateWrapper<EmojiItem>()
                        .eq(EmojiItem::getId, item.getId())
                        .set(EmojiItem::getDeleteState, (byte) 1));
            }
        }
        for (int sort = 0; sort < imageUrls.size(); sort++) {
            String url = imageUrls.get(sort);
            EmojiItem existing = existingByUrl.get(url);
            if (existing == null) {
                EmojiItem item = new EmojiItem();
                item.setShopId(shopId);
                item.setImageUrl(url);
                item.setSort(sort);
                emojiItemMapper.insert(item);
            } else {
                emojiItemMapper.update(null, new LambdaUpdateWrapper<EmojiItem>()
                        .eq(EmojiItem::getId, existing.getId())
                        .set(EmojiItem::getSort, sort)
                        .set(EmojiItem::getDeleteState, (byte) 0));
            }
        }
    }

    private static final class PublishedPayload {
        private final String name;
        private final String description;
        private final String category;
        private final String coverUrl;
        private final Integer price;
        private final List<String> imageUrls;

        private PublishedPayload(String name, String description, String category, String coverUrl,
                                 Integer price, List<String> imageUrls) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.coverUrl = coverUrl;
            this.price = price;
            this.imageUrls = imageUrls;
        }
    }

    private EmojiShop getOwnDraft(Long operatorUserId, Long draftId) {
        if (draftId == null || draftId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        EmojiShop draft = emojiShopMapper.selectById(draftId);
        boolean isOwnDraft = draft != null
                && (draft.getDeleteState() == null || draft.getDeleteState() != 1)
                && Constant.SHOP_STATUS_DRAFT.equals(draft.getStatus())
                && operatorUserId.equals(draft.getUploadUserId());
        if (!isOwnDraft) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_NOT_EXISTS));
        }
        return draft;
    }

    private DraftPayload normalizeDraftPayload(SaveEmojiShopDraftRequest req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.length() > 20) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包名称最多 20 字"));
        }
        String description = req.getDescription() == null ? "" : req.getDescription().trim();
        if (description.length() > 50) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "表情包说明最多 50 字"));
        }
        String category = req.getCategory() == null ? "" : req.getCategory().trim();
        if (StringUtils.hasText(category) && EmojiShopCategory.fromCode(category) == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请选择有效的表情包分类"));
        }
        int price = req.getPrice() == null ? 0 : req.getPrice();
        if (price < Constant.EMOJI_SHOP_PRICE_MIN || price > Constant.EMOJI_SHOP_PRICE_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_PRICE_INVALID));
        }
        String coverUrl = req.getCoverUrl() == null ? "" : req.getCoverUrl().trim();
        if (StringUtils.hasText(coverUrl)) {
            validateShopUrl(coverUrl, "草稿封面图 URL 非法");
        }
        List<String> imageUrls = normalizeShopImageUrls(req.getImageUrls());
        return new DraftPayload(StringUtils.hasText(name) ? name : DEFAULT_DRAFT_NAME,
                StringUtils.hasText(description) ? description : null,
                StringUtils.hasText(category) ? category : EmojiShopCategory.OTHER.getCode(),
                StringUtils.hasText(coverUrl) ? coverUrl : null, price, imageUrls);
    }

    private List<String> normalizeShopImageUrls(List<String> rawUrls) {
        if (rawUrls == null || rawUrls.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> uniqueUrls = new LinkedHashSet<>();
        for (String rawUrl : rawUrls) {
            String url = rawUrl == null ? "" : rawUrl.trim();
            if (!StringUtils.hasText(url)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址不能为空"));
            }
            validateShopUrl(url, "草稿包内图片 URL 非法");
            uniqueUrls.add(url);
        }
        if (uniqueUrls.size() > Constant.EMOJI_SHOP_ITEM_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_ITEMS_LIMIT));
        }
        return new ArrayList<>(uniqueUrls);
    }

    private void replaceShopItems(Long shopId, List<String> imageUrls) {
        emojiItemMapper.update(null, new LambdaUpdateWrapper<EmojiItem>()
                .eq(EmojiItem::getShopId, shopId)
                .ne(EmojiItem::getDeleteState, 1)
                .set(EmojiItem::getDeleteState, (byte) 1));
        int sort = 0;
        for (String url : imageUrls) {
            EmojiItem item = new EmojiItem();
            item.setShopId(shopId);
            item.setImageUrl(url);
            item.setSort(sort++);
            emojiItemMapper.insert(item);
        }
    }

    private CreateEmojiShopRequest toCreateRequest(SaveEmojiShopDraftRequest req) {
        CreateEmojiShopRequest request = new CreateEmojiShopRequest();
        request.setName(req.getName());
        request.setDescription(req.getDescription());
        request.setCategory(req.getCategory());
        request.setCoverUrl(req.getCoverUrl());
        request.setPrice(req.getPrice());
        request.setImageUrls(req.getImageUrls());
        return request;
    }

    private static final class DraftPayload {
        private final String name;
        private final String description;
        private final String category;
        private final String coverUrl;
        private final Integer price;
        private final List<String> imageUrls;

        private DraftPayload(String name, String description, String category, String coverUrl, Integer price,
                             List<String> imageUrls) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.coverUrl = coverUrl;
            this.price = price;
            this.imageUrls = imageUrls;
        }
    }

    // 作者上架后自动入库，聊天可直接使用
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

    // 待审核表情包文本审核：通过 → 上架并发作者包；拒绝 → 软删
    private void processPendingShopTextAudit(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return;
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        if (shop == null
                || !Constant.SHOP_STATUS_PENDING.equals(shop.getStatus())
                || (shop.getDeleteState() != null && shop.getDeleteState() == 1)) {
            return;
        }
        try {
            String nameReject = economyAiGatewayService.validateText(shop.getName());
            if (StringUtils.hasText(nameReject)) {
                rejectPendingShop(shopId, "名称未通过审核");
                return;
            }
            String description = shop.getDescription() == null ? "" : shop.getDescription().trim();
            if (StringUtils.hasText(description)) {
                String descReject = economyAiGatewayService.validateText(description);
                if (StringUtils.hasText(descReject)) {
                    rejectPendingShop(shopId, "说明未通过审核");
                    return;
                }
            }
            approvePendingShop(shopId);
        } catch (Exception ex) {
            log.warn("表情包异步文本审核暂失败，将由定时任务重试 shopId={}: {}", shopId, ex.getMessage());
        }
    }

    private void approvePendingShop(Long shopId) {
        int updated = emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId)
                .eq(EmojiShop::getStatus, Constant.SHOP_STATUS_PENDING)
                .ne(EmojiShop::getDeleteState, 1)
                .set(EmojiShop::getStatus, Constant.SHOP_STATUS_ONLINE));
        if (updated != 1) {
            return;
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        if (shop == null) {
            return;
        }
        if (shop.getUploadUserId() != null) {
            grantAuthorPackQuietly(shop.getUploadUserId(), shop.getId());
        }
        indexEmojiRagQuietly(shop);
        invalidateShopListCache();
        log.info("表情包异步审核通过已上架 shopId={}", shopId);
    }

    private void rejectPendingShop(Long shopId, String reason) {
        int updated = emojiShopMapper.update(null, new LambdaUpdateWrapper<EmojiShop>()
                .eq(EmojiShop::getId, shopId)
                .eq(EmojiShop::getStatus, Constant.SHOP_STATUS_PENDING)
                .ne(EmojiShop::getDeleteState, 1)
                .set(EmojiShop::getDeleteState, (byte) 1));
        if (updated == 1) {
            invalidateShopListCache();
            log.info("表情包异步审核未通过已撤回 shopId={} reason={}", shopId, reason);
        }
    }

    // 兜底：超时仍待审核的表情包重试文本审核
    @Scheduled(fixedDelay = 60000L, initialDelay = 45000L)
    public void retryPendingShopTextAudits() {
        Date before = new Date(System.currentTimeMillis() - 30000L);
        List<EmojiShop> pending = emojiShopMapper.selectList(new LambdaQueryWrapper<EmojiShop>()
                .eq(EmojiShop::getStatus, Constant.SHOP_STATUS_PENDING)
                .ne(EmojiShop::getDeleteState, 1)
                .le(EmojiShop::getUpdateTime, before)
                .orderByAsc(EmojiShop::getId)
                .last("LIMIT 20"));
        for (EmojiShop shop : pending) {
            Long shopId = shop.getId();
            emojiShopTextAuditExecutor.execute(() -> processPendingShopTextAudit(shopId));
        }
    }

    private void indexEmojiRagQuietly(EmojiShop shop) {
        try {
            RagEmojiIndexDTO payload = new RagEmojiIndexDTO();
            payload.setShopId(shop.getId());
            payload.setName(shop.getName());
            payload.setDescription(shop.getDescription());
            payload.setCategory(shop.getCategory());
            payload.setCoverUrl(shop.getCoverUrl());
            economyAiGatewayService.indexEmojiRag(payload);
        } catch (Exception e) {
            log.warn("表情包发布后 RAG 索引失败 shopId={}: {}", shop.getId(), e.getMessage());
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
        UserInternalVO operator = userInternalFeignClient.getById(operatorUserId);
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
    public PageResult<EmojiShopListItemVO> queryShopList(Long loginUserId, String sort, String category,
                                                         String keyword, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requestedPageSize = pageSize == null || pageSize < 1 ? DEFAULT_SHOP_PAGE_SIZE : pageSize;
        int validPageSize = PageUtils.getValidPageSize(requestedPageSize);
        String validSort = normalizeSort(sort);
        String validCategory = normalizeCategory(category);
        String validKeyword = keyword == null ? "" : keyword.trim();
        String cacheKey = buildShopListCacheKey(validSort, validCategory, validKeyword, validPageNum, validPageSize);
        PageResult<EmojiShopListItemVO> publicPage = readShopListCache(cacheKey);
        if (publicPage == null) {
            publicPage = queryPublicShopListWithCacheLock(cacheKey, validSort, validCategory, validKeyword,
                    validPageNum, validPageSize);
        }
        List<EmojiShopListItemVO> records = applyOwnedState(publicPage.getRecords(), loginUserId);
        return new PageResult<>(records, publicPage.getTotal(), validPageNum, validPageSize,
                publicPage.getPages(), publicPage.getHasNextPage());
    }

    private PageResult<EmojiShopListItemVO> queryPublicShopListWithCacheLock(String cacheKey, String validSort,
                                                                             String validCategory, String validKeyword,
                                                                             int validPageNum, int validPageSize) {
        if (!StringUtils.hasText(cacheKey)) {
            return queryPublicShopList(validSort, validCategory, validKeyword, validPageNum, validPageSize);
        }
        String lockKey = Constant.REDIS_KEY_SHOP_LIST_LOCK + encodeCachePart(cacheKey);
        Boolean locked = Boolean.FALSE;
        try {
            locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                    Constant.REDIS_TTL_SHOP_LIST_LOCK, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                PageResult<EmojiShopListItemVO> publicPage = queryPublicShopList(validSort, validCategory,
                        validKeyword, validPageNum, validPageSize);
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
        PageResult<EmojiShopListItemVO> publicPage = queryPublicShopList(validSort, validCategory,
                validKeyword, validPageNum, validPageSize);
        writeShopListCache(cacheKey, publicPage);
        return publicPage;
    }

    private PageResult<EmojiShopListItemVO> queryPublicShopList(String validSort, String validCategory,
                                                                String validKeyword, int validPageNum,
                                                                int validPageSize) {
        if (StringUtils.hasText(validKeyword)) {
            return queryKeywordShopList(validSort, validCategory, validKeyword,
                    validPageNum, validPageSize);
        }
        LambdaQueryWrapper<EmojiShop> qw = newPublicShopQuery(validCategory);
        return queryShopPage(qw, validSort, validPageNum, validPageSize);
    }

    private PageResult<EmojiShopListItemVO> queryKeywordShopList(
            String sort, String category, String keyword, int pageNum, int pageSize) {
        List<EmojiShop> ranked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        appendShopMatches(ranked, seen, category, sort,
                newPublicShopQuery(category).eq(EmojiShop::getName, keyword));
        appendShopMatches(ranked, seen, category, sort,
                newPublicShopQuery(category).like(EmojiShop::getName, keyword));

        var authorPage = userInternalFeignClient.searchByKeyword(
                keyword, 1, SEMANTIC_SEARCH_CANDIDATE_LIMIT);
        if (authorPage != null && authorPage.getRecords() != null) {
            List<Long> exactAuthorIds = authorPage.getRecords().stream()
                    .filter(user -> keyword.equalsIgnoreCase(
                            user.getNickname() == null ? "" : user.getNickname().trim()))
                    .map(UserInternalVO::getId)
                    .toList();
            List<Long> fuzzyAuthorIds = authorPage.getRecords().stream()
                    .map(UserInternalVO::getId)
                    .filter(id -> !exactAuthorIds.contains(id))
                    .toList();
            if (!exactAuthorIds.isEmpty()) {
                appendShopMatches(ranked, seen, category, sort,
                        newPublicShopQuery(category).in(EmojiShop::getUploadUserId, exactAuthorIds));
            }
            if (!fuzzyAuthorIds.isEmpty()) {
                appendShopMatches(ranked, seen, category, sort,
                        newPublicShopQuery(category).in(EmojiShop::getUploadUserId, fuzzyAuthorIds));
            }
        }
        return toShopListPage(ranked, pageNum, pageSize);
    }

    private void appendShopMatches(List<EmojiShop> target, Set<Long> seen, String category,
                                   String sort, LambdaQueryWrapper<EmojiShop> query) {
        List<EmojiShop> matches;
        if ("comprehensive".equals(sort)) {
            matches = emojiShopMapper.selectList(query);
            sortByComprehensiveScore(matches);
        } else {
            applySort(query, sort);
            matches = emojiShopMapper.selectList(query);
        }
        for (EmojiShop shop : matches) {
            if (shop.getId() != null && seen.add(shop.getId())) {
                target.add(shop);
            }
        }
    }

    private LambdaQueryWrapper<EmojiShop> newPublicShopQuery(String category) {
        LambdaQueryWrapper<EmojiShop> query = new LambdaQueryWrapper<EmojiShop>()
                .eq(EmojiShop::getStatus, Constant.SHOP_STATUS_ONLINE)
                .ne(EmojiShop::getDeleteState, 1);
        if (!CATEGORY_ALL.equals(category)) {
            query.eq(EmojiShop::getCategory, category);
        }
        return query;
    }

    private PageResult<EmojiShopListItemVO> queryShopPage(LambdaQueryWrapper<EmojiShop> query, String sort,
                                                           int pageNum, int pageSize) {
        if ("comprehensive".equals(sort)) {
            List<EmojiShop> shops = emojiShopMapper.selectList(query);
            sortByComprehensiveScore(shops);
            return toShopListPage(shops, pageNum, pageSize);
        }
        applySort(query, sort);
        Page<EmojiShop> page = emojiShopMapper.selectPage(new Page<>(pageNum, pageSize), query);
        List<EmojiShopListItemVO> records = enrichPublicListItems(page.getRecords());
        return new PageResult<>(records, page.getTotal(), pageNum, pageSize, page.getPages(), page.hasNext());
    }

    // 综合排序：销量 60%，最近 30 天线性新鲜度 30%，低价优势 10%
    private void sortByComprehensiveScore(List<EmojiShop> shops) {
        if (shops == null || shops.size() < 2) {
            return;
        }
        int maxSalesCount = 0;
        int maxPrice = 0;
        for (EmojiShop shop : shops) {
            maxSalesCount = Math.max(maxSalesCount, safeInt(shop.getSalesCount()));
            maxPrice = Math.max(maxPrice, safeInt(shop.getPrice()));
        }
        long now = System.currentTimeMillis();
        int finalMaxSalesCount = maxSalesCount;
        int finalMaxPrice = maxPrice;
        shops.sort((left, right) -> {
            int scoreCompare = Double.compare(
                    calculateComprehensiveScore(right, finalMaxSalesCount, finalMaxPrice, now),
                    calculateComprehensiveScore(left, finalMaxSalesCount, finalMaxPrice, now));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int salesCompare = Integer.compare(safeInt(right.getSalesCount()), safeInt(left.getSalesCount()));
            if (salesCompare != 0) {
                return salesCompare;
            }
            int timeCompare = compareCreateTimeDesc(left, right);
            if (timeCompare != 0) {
                return timeCompare;
            }
            int priceCompare = Integer.compare(safeInt(left.getPrice()), safeInt(right.getPrice()));
            if (priceCompare != 0) {
                return priceCompare;
            }
            return compareIdDesc(left.getId(), right.getId());
        });
    }

    private double calculateComprehensiveScore(EmojiShop shop, int maxSalesCount, int maxPrice, long now) {
        double salesScore = maxSalesCount <= 0 ? 0D : (double) safeInt(shop.getSalesCount()) / maxSalesCount;
        double recencyScore = calculateRecencyScore(shop.getCreateTime(), now);
        double priceScore = maxPrice <= 0 ? 1D : 1D - ((double) safeInt(shop.getPrice()) / maxPrice);
        return salesScore * COMPREHENSIVE_SALES_WEIGHT
                + recencyScore * COMPREHENSIVE_RECENCY_WEIGHT
                + priceScore * COMPREHENSIVE_LOW_PRICE_WEIGHT;
    }

    private double calculateRecencyScore(Date createTime, long now) {
        if (createTime == null) {
            return 0D;
        }
        long windowMillis = TimeUnit.DAYS.toMillis(COMPREHENSIVE_RECENCY_DAYS);
        long ageMillis = Math.max(0L, now - createTime.getTime());
        return Math.max(0D, 1D - (double) ageMillis / windowMillis);
    }

    private PageResult<EmojiShopListItemVO> toShopListPage(List<EmojiShop> shops, int pageNum, int pageSize) {
        List<EmojiShop> safeShops = shops == null ? Collections.emptyList() : shops;
        long total = safeShops.size();
        int fromIndex = Math.min((pageNum - 1) * pageSize, safeShops.size());
        int toIndex = Math.min(fromIndex + pageSize, safeShops.size());
        long pages = total == 0 ? 0L : (total + pageSize - 1) / pageSize;
        return new PageResult<>(enrichPublicListItems(safeShops.subList(fromIndex, toIndex)), total, pageNum,
                pageSize, pages, toIndex < safeShops.size());
    }

    // 普通名称匹配无结果时，AI 仅对本域已筛出的上架候选排序，最终可见性仍由本服务复查
    private PageResult<EmojiShopListItemVO> querySemanticShopList(String validSort, String validCategory,
                                                                   String keyword, int pageNum, int pageSize) {
        PageResult<EmojiShopListItemVO> vectorPage = querySemanticShopListByIds(
                economyAiGatewayService.ragVectorSearchEmojis(keyword), validCategory, pageNum, pageSize);
        if (vectorPage != null) {
            return vectorPage;
        }
        LambdaQueryWrapper<EmojiShop> candidateWrapper = newPublicShopQuery(validCategory);
        applySort(candidateWrapper, validSort);
        List<EmojiShop> candidates = emojiShopMapper.selectPage(
                new Page<>(1, SEMANTIC_SEARCH_CANDIDATE_LIMIT, false), candidateWrapper).getRecords();
        if (candidates == null || candidates.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize, 0L, false);
        }
        List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
        for (EmojiShop candidate : candidates) {
            Map<String, Object> item = new HashMap<>(2);
            item.put("candidateId", candidate.getId());
            item.put("text", candidate.getName() + "\n" + candidate.getCategory() + "\n"
                    + (candidate.getDescription() == null ? "" : candidate.getDescription()));
            payload.add(item);
        }
        List<Long> rankedIds;
        try {
            rankedIds = economyAiGatewayService.rankSemanticCandidates(keyword, payload);
        } catch (RuntimeException exception) {
            log.warn("表情包 AI 语义检索失败: {}", exception.getMessage());
            rankedIds = Collections.emptyList();
        }
        if (rankedIds == null || rankedIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize, 0L, false);
        }
        List<EmojiShop> verified = emojiShopMapper.selectList(newPublicShopQuery(validCategory)
                .in(EmojiShop::getId, rankedIds));
        Map<Long, EmojiShop> verifiedById = new HashMap<>();
        for (EmojiShop shop : verified) {
            verifiedById.put(shop.getId(), shop);
        }
        List<EmojiShop> ranked = new ArrayList<>();
        for (Long shopId : rankedIds) {
            EmojiShop shop = verifiedById.get(shopId);
            if (shop != null) {
                ranked.add(shop);
            }
        }
        long total = ranked.size();
        int fromIndex = Math.min((pageNum - 1) * pageSize, ranked.size());
        int toIndex = Math.min(fromIndex + pageSize, ranked.size());
        long pages = total == 0 ? 0L : (total + pageSize - 1) / pageSize;
        List<EmojiShopListItemVO> records = enrichPublicListItems(ranked.subList(fromIndex, toIndex));
        return new PageResult<>(records, total, pageNum, pageSize, pages, toIndex < ranked.size());
    }

    private PageResult<EmojiShopListItemVO> querySemanticShopListByIds(List<Long> rankedIds, String category,
                                                                        int pageNum, int pageSize) {
        if (rankedIds == null || rankedIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<EmojiShop> verifiedQuery = newPublicShopQuery(category)
                .in(EmojiShop::getId, rankedIds);
        List<EmojiShop> verified = emojiShopMapper.selectList(verifiedQuery);
        Map<Long, EmojiShop> byId = new HashMap<>();
        for (EmojiShop shop : verified) {
            byId.put(shop.getId(), shop);
        }
        List<EmojiShop> ranked = new ArrayList<>();
        for (Long shopId : rankedIds) {
            EmojiShop shop = byId.get(shopId);
            if (shop != null) {
                ranked.add(shop);
            }
        }
        if (ranked.isEmpty()) {
            return null;
        }
        int fromIndex = Math.min((pageNum - 1) * pageSize, ranked.size());
        int toIndex = Math.min(fromIndex + pageSize, ranked.size());
        long total = ranked.size();
        long pages = (total + pageSize - 1) / pageSize;
        List<EmojiShopListItemVO> records = enrichPublicListItems(ranked.subList(fromIndex, toIndex));
        return new PageResult<>(records, total, pageNum, pageSize, pages, toIndex < ranked.size());
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
        // 上架商品公开可见；下架商品仍允许作者、管理员和历史购买者只读查看
        if (!Constant.SHOP_STATUS_ONLINE.equals(shop.getStatus())) {
            boolean canView = loginUserId != null && isOwned(loginUserId, shopId);
            if (loginUserId != null && loginUserId.equals(shop.getUploadUserId())) {
                canView = true;
            }
            if (!canView && loginUserId != null) {
                UserInternalVO operator = userInternalFeignClient.getById(loginUserId);
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
            UserInternalVO uploader = userInternalFeignClient.getById(shop.getUploadUserId());
            if (uploader != null) {
                uploaderName = uploader.getNickname();
                uploaderAvatar = uploader.getAvatarUrl();
                VipStatusVO vipStatus = vipSubscribeService.status(shop.getUploadUserId());
                if (vipStatus != null) {
                    uploaderVipTier = vipStatus.getVipTier();
                    uploaderVipExpire = vipStatus.getVipExpireAt();
                }
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

    // 把 EmojiShop 列表批量补齐上传者资料; owned 按请求用户另行计算, 不写入公开缓存
    private List<EmojiShopListItemVO> enrichPublicListItems(List<EmojiShop> shops) {
        if (shops.isEmpty()) return Collections.emptyList();
        // 批量查上传者资料, 避免同一上传者重复查询
        Set<Long> uploaderIds = new HashSet<>();
        for (EmojiShop s : shops) {
            if (s.getUploadUserId() != null) uploaderIds.add(s.getUploadUserId());
        }
        Map<Long, String> uploaderNames = new HashMap<>();
        Map<Long, String> uploaderAvatars = new HashMap<>();
        if (!uploaderIds.isEmpty()) {
            for (Long uid : uploaderIds) {
                UserInternalVO u = userInternalFeignClient.getById(uid);
                if (u != null) {
                    uploaderNames.put(uid, u.getNickname());
                    uploaderAvatars.put(uid, u.getAvatarUrl());
                }
            }
        }
        List<EmojiShopListItemVO> result = new ArrayList<>(shops.size());
        for (EmojiShop s : shops) {
            result.add(new EmojiShopListItemVO(s.getId(), s.getName(), s.getCategory(), s.getCoverUrl(), s.getPrice(),
                    s.getSalesCount(), s.getUploadUserId(),
                    s.getUploadUserId() == null ? null : uploaderNames.get(s.getUploadUserId()),
                    s.getUploadUserId() == null ? null : uploaderAvatars.get(s.getUploadUserId()),
                    false, s.getStatus(), s.getCreateTime()));
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
            result.add(new EmojiShopListItemVO(row.getId(), row.getName(), row.getCategory(), row.getCoverUrl(), row.getPrice(),
                    row.getSalesCount(), row.getUploadUserId(), row.getUploadUserNickname(),
                    row.getUploadUserAvatarUrl(),
                    owned, row.getStatus(), row.getCreateTime()));
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
        return checkOwnedEmojiAvailability(userId, shopId, url) == ShopEmojiAvailability.AVAILABLE;
    }

    @Override
    public ShopEmojiAvailabilityVO queryEmojiAvailability(Long shopId, String url) {
        String validUrl = url == null ? "" : url.trim();
        if (!StringUtils.hasText(validUrl)) {
            return new ShopEmojiAvailabilityVO(ShopEmojiAvailability.NOT_FOUND, shopId);
        }
        LambdaQueryWrapper<EmojiItem> query = new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getImageUrl, validUrl)
                .orderByDesc(EmojiItem::getId);
        if (shopId != null && shopId > 0) {
            query.eq(EmojiItem::getShopId, shopId);
        }
        List<EmojiItem> items = emojiItemMapper.selectList(query);
        if (items.isEmpty()) {
            return new ShopEmojiAvailabilityVO(ShopEmojiAvailability.NOT_FOUND, shopId);
        }
        ShopEmojiAvailabilityVO fallback = null;
        for (EmojiItem item : items) {
            EmojiShop shop = emojiShopMapper.selectById(item.getShopId());
            ShopEmojiAvailability status;
            if (shop == null || (shop.getDeleteState() != null && shop.getDeleteState() == 1)) {
                status = ShopEmojiAvailability.SERIES_DELETED;
            } else if (item.getDeleteState() != null && item.getDeleteState() == 1) {
                status = ShopEmojiAvailability.ITEM_DELETED;
            } else if (!Constant.SHOP_STATUS_ONLINE.equals(shop.getStatus())) {
                status = ShopEmojiAvailability.SERIES_OFFLINE;
            } else {
                status = ShopEmojiAvailability.AVAILABLE;
            }
            ShopEmojiAvailabilityVO current = new ShopEmojiAvailabilityVO(status, item.getShopId());
            if (status == ShopEmojiAvailability.AVAILABLE) {
                return current;
            }
            if (fallback == null || availabilityPriority(status) > availabilityPriority(fallback.getStatus())) {
                fallback = current;
            }
        }
        return fallback;
    }

    @Override
    public ShopEmojiAvailability checkOwnedEmojiAvailability(Long userId, Long shopId, String url) {
        if (userId == null || userId <= 0 || shopId == null || shopId <= 0) {
            return ShopEmojiAvailability.NOT_FOUND;
        }
        ShopEmojiAvailabilityVO availability = queryEmojiAvailability(shopId, url);
        if (availability.getStatus() != ShopEmojiAvailability.AVAILABLE) {
            return availability.getStatus();
        }
        EmojiShop shop = emojiShopMapper.selectById(shopId);
        boolean isAuthor = shop != null && userId.equals(shop.getUploadUserId());
        return isAuthor || isOwned(userId, shopId)
                ? ShopEmojiAvailability.AVAILABLE
                : ShopEmojiAvailability.NOT_OWNED;
    }

    private int availabilityPriority(ShopEmojiAvailability status) {
        if (status == ShopEmojiAvailability.SERIES_DELETED) {
            return 3;
        }
        if (status == ShopEmojiAvailability.ITEM_DELETED) {
            return 2;
        }
        if (status == ShopEmojiAvailability.SERIES_OFFLINE) {
            return 1;
        }
        return 0;
    }

    private String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort)) return "comprehensive";
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "comprehensive" -> "comprehensive";
            case "new", "published_desc" -> "published_desc";
            case "published_asc" -> "published_asc";
            case "price_asc" -> "price_asc";
            case "price_desc" -> "price_desc";
            case "hot", "sales_desc" -> "sales_desc";
            case "sales_asc" -> "sales_asc";
            default -> "comprehensive";
        };
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category) || CATEGORY_ALL.equalsIgnoreCase(category.trim())) {
            return CATEGORY_ALL;
        }
        EmojiShopCategory value = EmojiShopCategory.fromCode(category);
        return value == null ? CATEGORY_ALL : value.getCode();
    }

    private void applySort(LambdaQueryWrapper<EmojiShop> qw, String sort) {
        switch (sort) {
            case "published_asc":
                qw.orderByAsc(EmojiShop::getCreateTime)
                        .orderByDesc(EmojiShop::getPrice)
                        .orderByDesc(EmojiShop::getId);
                break;
            case "price_asc":
                qw.orderByAsc(EmojiShop::getPrice)
                        .orderByDesc(EmojiShop::getCreateTime)
                        .orderByDesc(EmojiShop::getId);
                break;
            case "price_desc":
                qw.orderByDesc(EmojiShop::getPrice)
                        .orderByDesc(EmojiShop::getCreateTime)
                        .orderByDesc(EmojiShop::getId);
                break;
            case "sales_asc":
                qw.orderByAsc(EmojiShop::getSalesCount)
                        .orderByDesc(EmojiShop::getPrice)
                        .orderByDesc(EmojiShop::getCreateTime)
                        .orderByDesc(EmojiShop::getId);
                break;
            case "sales_desc":
                qw.orderByDesc(EmojiShop::getSalesCount)
                        .orderByDesc(EmojiShop::getPrice)
                        .orderByDesc(EmojiShop::getCreateTime)
                        .orderByDesc(EmojiShop::getId);
                break;
            case "comprehensive":
            case "published_desc":
            default:
                qw.orderByDesc(EmojiShop::getCreateTime)
                        .orderByDesc(EmojiShop::getPrice)
                        .orderByDesc(EmojiShop::getId);
                break;
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private int compareCreateTimeDesc(EmojiShop left, EmojiShop right) {
        long leftTime = left.getCreateTime() == null ? Long.MIN_VALUE : left.getCreateTime().getTime();
        long rightTime = right.getCreateTime() == null ? Long.MIN_VALUE : right.getCreateTime().getTime();
        return Long.compare(rightTime, leftTime);
    }

    private int compareIdDesc(Long leftId, Long rightId) {
        long left = leftId == null ? Long.MIN_VALUE : leftId;
        long right = rightId == null ? Long.MIN_VALUE : rightId;
        return Long.compare(right, left);
    }

    // 校验 URL 必须是 OSS_PATH_EMOJI_SHOP 下的本站资源. 不允许外链 / 其他业务目录. 额外拒绝 .. / 反斜杠 / 控制字符等可能绕过前缀语义的路径段 防 CDN 端规范化后实际指向别处 .
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

    private String buildShopListCacheKey(String sort, String category, String keyword, int pageNum, int pageSize) {
        try {
            String version = stringRedisTemplate.opsForValue().get(Constant.REDIS_KEY_SHOP_LIST_VERSION);
            String safeVersion = StringUtils.hasText(version) ? version : "0";
            return Constant.REDIS_KEY_SHOP_LIST
                    + SHOP_LIST_CACHE_SCHEMA_VERSION
                    + ":"
                    + safeVersion
                    + ":"
                    + sort
                    + ":"
                    + category
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
