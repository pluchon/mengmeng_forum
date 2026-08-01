package org.pluchon.forum.service.impl.mascot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.AiCallState;
import org.pluchon.forum.common.enums.MascotRelatedRecommendationState;
import org.pluchon.forum.common.enums.MascotRelatedSelectionReason;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ForumMascotModel;
import org.pluchon.forum.entity.db.ForumCompanionSession;
import org.pluchon.forum.entity.db.ForumMascotRelatedRecommendation;
import org.pluchon.forum.entity.db.ForumMascotRelatedRecommendationItem;
import org.pluchon.forum.entity.db.ForumCompanionMessage;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.db.UserMascotPreference;
import org.pluchon.forum.entity.dto.ai.AiModelUsageDTO;
import org.pluchon.forum.entity.dto.ai.AiImageRequest;
import org.pluchon.forum.entity.dto.mascot.MascotChatRequest;
import org.pluchon.forum.entity.dto.mascot.MascotHistoryTurn;
import org.pluchon.forum.entity.dto.mascot.MascotRelatedRecommendationRequest;
import org.pluchon.forum.converter.ArticleBriefConverter;
import org.pluchon.forum.converter.ArticleInternalConverter;
import org.pluchon.forum.converter.MascotConverter;
import org.pluchon.forum.entity.vo.ai.AiCallBeginResult;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.mascot.MascotChatResponseVO;
import org.pluchon.forum.entity.vo.mascot.MascotModelPublicVO;
import org.pluchon.forum.entity.vo.mascot.MascotRelatedArticleCandidate;
import org.pluchon.forum.entity.vo.mascot.MascotRelatedRecommendationItemVO;
import org.pluchon.forum.entity.vo.mascot.MascotRelatedRecommendationVO;
import org.pluchon.forum.entity.vo.mascot.CompanionContextWindowVO;
import org.pluchon.forum.entity.vo.mascot.CompanionImageGalleryItemVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ForumMascotModelMapper;
import org.pluchon.forum.mapper.ForumCompanionSessionMapper;
import org.pluchon.forum.mapper.ForumCompanionMessageMapper;
import org.pluchon.forum.mapper.ForumMascotRelatedRecommendationItemMapper;
import org.pluchon.forum.mapper.ForumMascotRelatedRecommendationMapper;
import org.pluchon.forum.service.impl.remote.UserInternalLookupService;
import org.pluchon.forum.mapper.UserMascotPreferenceMapper;
import org.pluchon.forum.mapper.AiUsageDailyMapper;
import org.pluchon.forum.api.content.ArticleInternalVO;
import org.pluchon.forum.api.economy.VipTierSnapshotVO;
import org.pluchon.forum.cloud.feign.ArticleInternalFeignClient;
import org.pluchon.forum.cloud.feign.VipInternalFeignClient;
import org.pluchon.forum.common.utils.ArticleHotScoreUtils;
import org.pluchon.forum.entity.db.AiUsageDaily;
import org.pluchon.forum.entity.vo.mascot.MascotQuotaHintVO;
import org.springframework.dao.DuplicateKeyException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.service.impl.ai.AiCallRecordService;
import org.pluchon.forum.service.impl.ai.AiPointsBillingService;
import org.pluchon.forum.service.interfaces.mascot.CompanionMemoryService;
import org.pluchon.forum.service.interfaces.ai.AiQuotaService;
import org.pluchon.forum.service.interfaces.ai.AiCompanionApiService;
import org.pluchon.forum.service.interfaces.mascot.MascotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@Service
public class MascotServiceImpl implements MascotService {

    @Value("${forum.mascot.ai-url}")
    private String mascotAiUrl;

    @Value("${forum.ai.internal-key:}")
    private String internalKey;

    @Value("${forum.mascot.basic-daily-limit:30}")
    private int basicDailyLimit;

    @Value("${forum.mascot.treat-admin-as-vip:true}")
    private boolean treatAdminAsVip;

    @Autowired
    private RestTemplate forumRestTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiPointsBillingService aiPointsBillingService;

    @Resource
    private AiCallRecordService aiCallRecordService;

    @Resource
    private AiQuotaService aiQuotaService;

    @Resource
    private AiCompanionApiService aiCompanionApiService;

    @Resource
    private CompanionMemoryService companionMemoryService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ForumMascotModelMapper forumMascotModelMapper;

    @Resource
    private MascotArticleRagHelper mascotArticleRagHelper;

    @Resource
    private ForumCompanionSessionMapper forumCompanionSessionMapper;

    @Autowired
    private ForumCompanionMessageMapper forumCompanionMessageMapper;

    @Resource
    private ForumMascotRelatedRecommendationMapper mascotRelatedRecommendationMapper;

    @Resource
    private ForumMascotRelatedRecommendationItemMapper mascotRelatedRecommendationItemMapper;

    @Lazy
    @Resource
    private ArticleInternalFeignClient articleInternalFeignClient;

    @Resource
    private UserInternalLookupService userInternalLookupService;

    @Resource
    private UserMascotPreferenceMapper userMascotPreferenceMapper;

    @Resource
    private AiUsageDailyMapper aiUsageDailyMapper;

    @Resource
    private VipInternalFeignClient vipInternalFeignClient;

    @Override
    public List<MascotModelPublicVO> listPublicModels() {
        List<ForumMascotModel> list = forumMascotModelMapper.selectList(
                Wrappers.lambdaQuery(ForumMascotModel.class)
                        .eq(ForumMascotModel::getDeleteState, (byte) 0)
                        .eq(ForumMascotModel::getShelfStatus, (byte) 1)
                        .orderByAsc(ForumMascotModel::getSortOrder)
                        .orderByDesc(ForumMascotModel::getId));
        return list.stream().map(m -> {
            MascotModelPublicVO v = new MascotModelPublicVO();
            v.setId(m.getId());
            v.setCode(m.getCode());
            v.setName(m.getName());
            v.setModelRelPath(m.getModelRelPath());
            v.setModelScale(m.getModelScale());
            v.setPosX(m.getPosX());
            v.setPosY(m.getPosY());
            v.setStageWidth(m.getStageWidth());
            v.setStageHeight(m.getStageHeight());
            return v;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setUserMascotPreference(Long userId, Long mascotModelId) {
        if (userId == null || mascotModelId == null || mascotModelId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ForumMascotModel model = forumMascotModelMapper.selectById(mascotModelId);
        if (model == null || (model.getDeleteState() != null && model.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (model.getShelfStatus() == null || model.getShelfStatus() != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅可选择已上架的看板娘"));
        }
        User exists = userInternalLookupService.getById(userId);
        if (exists == null || (exists.getDeleteState() != null && exists.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        UserMascotPreference pref = userMascotPreferenceMapper.selectByUserId(userId);
        if (pref == null) {
            try {
                userMascotPreferenceMapper.insertPreference(userId, mascotModelId);
            } catch (DuplicateKeyException ex) {
                userMascotPreferenceMapper.updatePreference(userId, mascotModelId);
            }
        } else {
            userMascotPreferenceMapper.updatePreference(userId, mascotModelId);
        }
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
    }

    @Override
    public MascotQuotaHintVO quotaHintForLlmRoute(Long userId, String llmRoute) {
        MascotQuotaHintVO vo = new MascotQuotaHintVO();
        vo.setPercent(0);
        vo.setCanUsePointsPay(false);
        vo.setQuotaLabel("");
        if (userId == null || userId <= 0) {
            return vo;
        }
        VipTierSnapshotVO tier = vipInternalFeignClient.tierSnapshot(userId);
        if (tier == null || !tier.isVipActive()
                || (!Constant.VIP_TIER_PRO.equals(tier.getVipTier())
                && !Constant.VIP_TIER_MAX.equals(tier.getVipTier()))) {
            return vo;
        }
        // AI 域本地读日用量；限额与 economy 配额配置对齐的常用默认值
        int limit = Constant.VIP_TIER_MAX.equals(tier.getVipTier()) ? 300 : 100;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<AiUsageDaily> rows = aiUsageDailyMapper.selectPage(new Page<>(1, 1, false),
                Wrappers.lambdaQuery(AiUsageDaily.class)
                        .eq(AiUsageDaily::getUserId, userId)
                        .eq(AiUsageDaily::getUsageDate, today)
                        .eq(AiUsageDaily::getDeleteState, 0)).getRecords();
        int used = 0;
        if (!rows.isEmpty() && rows.get(0).getAdvancedLlmUsed() != null) {
            used = rows.get(0).getAdvancedLlmUsed();
        }
        int percent = limit > 0 ? Math.min(100, (int) Math.round(used * 100.0 / limit)) : 0;
        vo.setPercent(percent);
        vo.setQuotaLabel("Qwen 深度写作");
        vo.setCanUsePointsPay(percent >= 95);
        return vo;
    }

    /** 用户确认后的相关帖子检索；结果项与选择原因在同一事务内保存。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MascotRelatedRecommendationVO recommendRelatedArticles(
            User user, MascotRelatedRecommendationRequest request) {
        Long sessionId = request.getSessionId();
        requireOwnedSession(user.getId(), sessionId);
        requireOwnedAssistantMessage(sessionId, request.getSourceMessageId());

        String query = normalizeRelatedQuery(request.getQuery());
        ForumMascotRelatedRecommendation existing = mascotRelatedRecommendationMapper.selectOne(
                Wrappers.lambdaQuery(ForumMascotRelatedRecommendation.class)
                        .eq(ForumMascotRelatedRecommendation::getUserId, user.getId())
                        .eq(ForumMascotRelatedRecommendation::getCompanionSessionId, sessionId)
                        .eq(ForumMascotRelatedRecommendation::getSourceMessageId, request.getSourceMessageId())
                        .eq(ForumMascotRelatedRecommendation::getDeleteState, (byte) 0)
                        .orderByDesc(ForumMascotRelatedRecommendation::getId)
                        .last("LIMIT 1"));
        if (existing != null) {
            return listRelatedRecommendations(user, sessionId).stream()
                    .filter(item -> existing.getId().equals(item.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ApplicationException(Result.fail(
                            ResultCode.FAILED_MASCOT_AI, "相关帖子检索结果读取失败")));
        }
        List<MascotRelatedArticleCandidate> candidates = mascotArticleRagHelper.findConfirmedRelatedCandidates(query);
        List<RelatedArticleSelection> selections = selectRelatedArticles(candidates);

        ForumMascotRelatedRecommendation recommendation = new ForumMascotRelatedRecommendation();
        recommendation.setUserId(user.getId());
        recommendation.setCompanionSessionId(sessionId);
        recommendation.setSourceMessageId(request.getSourceMessageId());
        recommendation.setQuery(query);
        recommendation.setResultState((selections.isEmpty()
                ? MascotRelatedRecommendationState.EMPTY : MascotRelatedRecommendationState.FOUND).name());
        recommendation.setResultCount(selections.size());
        recommendation.setDeleteState((byte) 0);
        mascotRelatedRecommendationMapper.insert(recommendation);

        for (int index = 0; index < selections.size(); index++) {
            RelatedArticleSelection selection = selections.get(index);
            ForumMascotRelatedRecommendationItem item = new ForumMascotRelatedRecommendationItem();
            item.setRecommendationId(recommendation.getId());
            item.setArticleId(selection.candidate().getArticle().getId());
            item.setDisplayOrder(index + 1);
            item.setSelectionReason(selection.reason().name());
            item.setDeleteState((byte) 0);
            mascotRelatedRecommendationItemMapper.insert(item);
        }
        return buildRelatedRecommendationVO(recommendation, selections);
    }

    @Override
    public List<MascotRelatedRecommendationVO> listRelatedRecommendations(User user, Long sessionId) {
        requireOwnedSession(user.getId(), sessionId);
        List<ForumMascotRelatedRecommendation> recommendations = mascotRelatedRecommendationMapper.selectList(
                Wrappers.lambdaQuery(ForumMascotRelatedRecommendation.class)
                        .eq(ForumMascotRelatedRecommendation::getUserId, user.getId())
                        .eq(ForumMascotRelatedRecommendation::getCompanionSessionId, sessionId)
                        .eq(ForumMascotRelatedRecommendation::getDeleteState, (byte) 0)
                        .orderByDesc(ForumMascotRelatedRecommendation::getCreateTime)
                        .orderByDesc(ForumMascotRelatedRecommendation::getId));
        if (recommendations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> recommendationIds = recommendations.stream()
                .map(ForumMascotRelatedRecommendation::getId)
                .toList();
        List<ForumMascotRelatedRecommendationItem> savedItems = mascotRelatedRecommendationItemMapper.selectList(
                Wrappers.lambdaQuery(ForumMascotRelatedRecommendationItem.class)
                        .in(ForumMascotRelatedRecommendationItem::getRecommendationId, recommendationIds)
                        .eq(ForumMascotRelatedRecommendationItem::getDeleteState, (byte) 0)
                        .orderByAsc(ForumMascotRelatedRecommendationItem::getRecommendationId)
                        .orderByAsc(ForumMascotRelatedRecommendationItem::getDisplayOrder));
        Map<Long, List<ForumMascotRelatedRecommendationItem>> itemsByRecommendationId = new HashMap<>();
        Set<Long> articleIds = new HashSet<>();
        for (ForumMascotRelatedRecommendationItem savedItem : savedItems) {
            itemsByRecommendationId.computeIfAbsent(savedItem.getRecommendationId(), key -> new ArrayList<>())
                    .add(savedItem);
            articleIds.add(savedItem.getArticleId());
        }

        Map<Long, Article> articlesById = new HashMap<>();
        if (!articleIds.isEmpty()) {
            List<ArticleInternalVO> vos = articleInternalFeignClient.listByIds(new ArrayList<>(articleIds));
            if (vos != null) {
                for (ArticleInternalVO vo : vos) {
                    Article article = ArticleInternalConverter.toArticleShell(vo);
                    if (article == null || article.getId() == null) {
                        continue;
                    }
                    // Feign listByIds 已排除逻辑删除；壳对象仍按 deleteState 兜底
                    if (article.getDeleteState() != null && article.getDeleteState() == 1) {
                        continue;
                    }
                    articlesById.put(article.getId(), article);
                }
            }
        }
        Set<Long> authorIds = new HashSet<>();
        for (Article article : articlesById.values()) {
            authorIds.add(article.getUserId());
        }
        Map<Long, User> usersById = authorIds.isEmpty() ? Map.of() : userInternalLookupService.loadActiveUsers(authorIds);

        List<MascotRelatedRecommendationVO> result = new ArrayList<>();
        Set<Long> loadedSourceMessageIds = new HashSet<>();
        Set<String> legacyLoadedQueries = new HashSet<>();
        for (ForumMascotRelatedRecommendation recommendation : recommendations) {
            Long sourceMessageId = recommendation.getSourceMessageId();
            if (sourceMessageId != null && !loadedSourceMessageIds.add(sourceMessageId)) {
                continue;
            }
            if (sourceMessageId == null && !legacyLoadedQueries.add(normalizeRelatedQuery(recommendation.getQuery()))) {
                continue;
            }
            List<MascotRelatedRecommendationItemVO> items = new ArrayList<>();
            for (ForumMascotRelatedRecommendationItem savedItem : itemsByRecommendationId
                    .getOrDefault(recommendation.getId(), Collections.emptyList())) {
                Article article = articlesById.get(savedItem.getArticleId());
                if (article == null) {
                    continue;
                }
                MascotRelatedRecommendationItemVO item = new MascotRelatedRecommendationItemVO();
                item.setArticle(ArticleBriefConverter.toBriefVO(article));
                User author = usersById.get(article.getUserId());
                item.setAuthor(author == null ? null : new UserBriefVO(author));
                item.setSelectionReason(savedItem.getSelectionReason());
                items.add(item);
            }
            MascotRelatedRecommendationVO vo = new MascotRelatedRecommendationVO();
            vo.setId(recommendation.getId());
            vo.setSourceMessageId(sourceMessageId);
            vo.setQuery(recommendation.getQuery());
            vo.setResultState(recommendation.getResultState());
            vo.setItems(items);
            vo.setCreateTime(recommendation.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    private String normalizeRelatedQuery(String query) {
        return query == null ? "" : query.trim().replaceAll("\\s+", " ");
    }

    private void requireOwnedAssistantMessage(Long sessionId, Long messageId) {
        ForumCompanionMessage source = forumCompanionMessageMapper.selectOne(
                Wrappers.lambdaQuery(ForumCompanionMessage.class)
                        .eq(ForumCompanionMessage::getId, messageId)
                        .eq(ForumCompanionMessage::getSessionId, sessionId)
                        .eq(ForumCompanionMessage::getRole, "assistant")
                        .eq(ForumCompanionMessage::getDeleteState, (byte) 0));
        if (source == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "来源消息不存在"));
        }
    }

    private void requireOwnedSession(Long userId, Long sessionId) {
        ForumCompanionSession session = forumCompanionSessionMapper.selectOne(
                Wrappers.lambdaQuery(ForumCompanionSession.class)
                        .eq(ForumCompanionSession::getId, sessionId)
                        .eq(ForumCompanionSession::getUserId, userId)
                        .eq(ForumCompanionSession::getDeleteState, (byte) 0));
        if (session == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "会话不存在或无权访问"));
        }
    }

    private List<RelatedArticleSelection> selectRelatedArticles(
            List<MascotRelatedArticleCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<MascotRelatedArticleCandidate> unique = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (MascotRelatedArticleCandidate candidate : candidates) {
            Article article = candidate.getArticle();
            if (article != null && article.getId() != null && seenIds.add(article.getId())) {
                unique.add(candidate);
            }
        }
        if (unique.size() <= 5) {
            List<RelatedArticleSelection> selected = new ArrayList<>();
            for (MascotRelatedArticleCandidate candidate : unique) {
                selected.add(new RelatedArticleSelection(candidate, MascotRelatedSelectionReason.RELEVANCE));
            }
            return selected;
        }

        List<MascotRelatedArticleCandidate> hot = unique.stream()
                .filter(candidate -> ArticleHotScoreUtils.computeHotScore(candidate.getArticle()) > 0D)
                .sorted(Comparator.comparingDouble(
                        (MascotRelatedArticleCandidate candidate) -> ArticleHotScoreUtils.computeHotScore(candidate.getArticle()))
                        .reversed())
                .toList();
        if (hot.isEmpty()) {
            return unique.stream()
                    .sorted(Comparator.comparing(
                            candidate -> candidate.getArticle().getCreateTime(),
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5)
                    .map(candidate -> new RelatedArticleSelection(candidate, MascotRelatedSelectionReason.RECENT))
                    .toList();
        }

        List<RelatedArticleSelection> selected = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        for (MascotRelatedArticleCandidate candidate : hot) {
            if (selected.size() >= 2) {
                break;
            }
            selected.add(new RelatedArticleSelection(candidate, MascotRelatedSelectionReason.HOT));
            selectedIds.add(candidate.getArticle().getId());
        }
        List<MascotRelatedArticleCandidate> recentPool = unique.stream()
                .filter(candidate -> !selectedIds.contains(candidate.getArticle().getId()))
                .sorted(Comparator.comparing(
                        candidate -> candidate.getArticle().getCreateTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(recentPool);
        for (MascotRelatedArticleCandidate candidate : recentPool) {
            if (selected.size() >= 5) {
                break;
            }
            selected.add(new RelatedArticleSelection(candidate, MascotRelatedSelectionReason.RECENT));
        }
        return selected;
    }

    private MascotRelatedRecommendationVO buildRelatedRecommendationVO(
            ForumMascotRelatedRecommendation recommendation,
            List<RelatedArticleSelection> selections) {
        Set<Long> userIds = new HashSet<>();
        for (RelatedArticleSelection selection : selections) {
            userIds.add(selection.candidate().getArticle().getUserId());
        }
        Map<Long, User> usersById = userIds.isEmpty() ? Map.of() : userInternalLookupService.loadActiveUsers(userIds);
        List<MascotRelatedRecommendationItemVO> items = new ArrayList<>();
        for (RelatedArticleSelection selection : selections) {
            Article article = selection.candidate().getArticle();
            MascotRelatedRecommendationItemVO item = new MascotRelatedRecommendationItemVO();
            item.setArticle(ArticleBriefConverter.toBriefVO(article));
            User author = usersById.get(article.getUserId());
            item.setAuthor(author == null ? null : new UserBriefVO(author));
            item.setSelectionReason(selection.reason().name());
            items.add(item);
        }
        MascotRelatedRecommendationVO vo = new MascotRelatedRecommendationVO();
        vo.setId(recommendation.getId());
        vo.setSourceMessageId(recommendation.getSourceMessageId());
        vo.setQuery(recommendation.getQuery());
        vo.setResultState(recommendation.getResultState());
        vo.setItems(items);
        vo.setCreateTime(recommendation.getCreateTime());
        return vo;
    }

    private record RelatedArticleSelection(
            MascotRelatedArticleCandidate candidate,
            MascotRelatedSelectionReason reason) {
    }

    private boolean isVip(User user) {
        Byte tier = user.getVipTier();
        if (tier != null && tier > 0) {
            Date exp = user.getVipExpireAt();
            if (exp == null || exp.after(new Date())) {
                return true;
            }
        }
        if (treatAdminAsVip) {
            return user.getIsAdmin() != null && user.getIsAdmin() == 1;
        }
        return false;
    }

    private String quotaKey(Long userId) {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        return Constant.REDIS_KEY_MASCOT_DAILY_CHAT + day + ":" + userId;
    }

    private void reserveBasicSlot(Long userId) {
        String key = quotaKey(userId);
        Long c = stringRedisTemplate.opsForValue().increment(key);
        if (c != null && c == 1L) {
            stringRedisTemplate.expire(key, Duration.ofHours(50));
        }
        if (c != null && c > basicDailyLimit) {
            stringRedisTemplate.opsForValue().decrement(key);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_QUOTA));
        }
    }

    private void releaseBasicSlot(Long userId) {
        try {
            stringRedisTemplate.opsForValue().decrement(quotaKey(userId));
        } catch (Exception e) {
            log.warn("看板娘额度回滚失败 userId={}", userId, e);
        }
    }

    private String normalizeSkill(MascotChatRequest request) {
        return companionMemoryService.normalizeSkill(request.getSkill());
    }

    private String normalizeLlmRoute(String route) {
        return "qwen-deep".equals(route) ? "qwen-deep" : "qwen-flash";
    }

    private int effectiveVipTier(User user) {
        Byte tier = user.getVipTier();
        int t = tier != null ? tier.intValue() : 0;
        if (treatAdminAsVip && user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return Math.max(t, Constant.VIP_TIER_MAX.intValue());
        }
        if (!isVip(user)) {
            return 0;
        }
        return Math.max(t, Constant.VIP_TIER_PRO.intValue());
    }

    private String resolveLlmRoute(MascotChatRequest request, boolean vip, String skill, User user) {
        if ("help".equals(skill) || !vip) {
            return "qwen-flash";
        }
        int tier = effectiveVipTier(user);
        if (tier < Constant.VIP_TIER_PRO || !isComplexMascotRequest(request)) {
            return "qwen-flash";
        }
        return "qwen-deep";
    }

    private boolean isComplexMascotRequest(MascotChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.length() >= 320) {
            return true;
        }
        int indicators = 0;
        for (String keyword : List.of("深入分析", "详细分析", "对比", "比较", "方案", "规划", "计划", "推理", "论证", "优缺点", "多步", "教程", "长文", "大纲")) {
            if (message.contains(keyword)) {
                indicators++;
            }
        }
        if (indicators >= 2 || message.contains("帮我写一篇") || message.contains("制定一个")) {
            return true;
        }
        int historyTurns = request.getHistory() == null ? 0 : request.getHistory().size();
        return historyTurns >= 4 && message.length() >= 120 && indicators >= 1;
    }

    private String featureCode(String skill) {
        return switch (skill) {
            case "help" -> "companion_help";
            case "drawing" -> "companion_image";
            case "chat" -> "companion_chat";
            default -> "companion_writing";
        };
    }

    private void reserveAiQuota(User user, String route, boolean[] reservedQwenFlash, boolean[] reservedAdvanced) {
        if (route.startsWith("qwen-deep")) {
            aiQuotaService.consumeAdvancedLlm(user);
            reservedAdvanced[0] = true;
        } else {
            aiQuotaService.consumeQwenFlash(user);
            reservedQwenFlash[0] = true;
        }
    }

    private void releaseAiQuota(User user, boolean reservedQwenFlash, boolean reservedAdvanced) {
        if (reservedQwenFlash) {
            aiQuotaService.releaseQwenFlash(user);
        }
        if (reservedAdvanced) {
            aiQuotaService.releaseAdvancedLlm(user);
        }
    }

    private String normalizeAppearanceForPy(MascotChatRequest request) {
        if (request.getMascotModelCode() != null && !request.getMascotModelCode().isBlank()) {
            return request.getMascotModelCode().trim();
        }
        if (request.getAppearance() != null && !request.getAppearance().isBlank()) {
            return request.getAppearance().trim();
        }
        return "snow_miku";
    }

    private List<Map<String, String>> toPyHistory(List<MascotHistoryTurn> history) {
        List<Map<String, String>> list = new ArrayList<>();
        if (history == null) {
            return list;
        }
        for (MascotHistoryTurn t : history) {
            if (t == null || t.getRole() == null || t.getContent() == null) {
                continue;
            }
            String role = t.getRole().trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String content = t.getContent().trim();
            if (content.isEmpty()) {
                continue;
            }
            Map<String, String> m = new HashMap<>(2);
            m.put("role", role);
            m.put("content", content.length() > 2000 ? content.substring(0, 2000) : content);
            list.add(m);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private AiModelUsageDTO parseUsage(Map<String, Object> body, String fallbackModel) {
        Object u = body.get("usage");
        AiModelUsageDTO dto = new AiModelUsageDTO();
        if (u instanceof Map<?, ?> um) {
            Object mc = um.get("model_code");
            if (mc == null) {
                mc = um.get("model");
            }
            if (mc != null) {
                dto.setModelCode(String.valueOf(mc));
            }
            dto.setInputTokens(intVal(um.get("input_tokens")));
            dto.setOutputTokens(intVal(um.get("output_tokens")));
            dto.setImageCount(intVal(um.get("images")));
            if (dto.getImageCount() == null) {
                dto.setImageCount(intVal(um.get("image_count")));
            }
            Object est = um.get("estimated");
            dto.setEstimated(est instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(est)));
            aiPointsBillingService.applyLatencyFromMap(dto, um);
        }
        return aiPointsBillingService.normalizeUsage(dto, fallbackModel);
    }

    private Map<String, Object> billMascotUsage(AiCallBeginResult begin, User user, String skill,
                                                AiModelUsageDTO usage, String relatedId,
                                                boolean usePointsBilling, long latencyMs) {
        return aiCallRecordService.settleSuccess(
                begin,
                user,
                featureCode(skill),
                usage,
                relatedId,
                Constant.POINTS_SOURCE_AI_COMPANION,
                usePointsBilling,
                latencyMs);
    }

    private String billingRelatedId(MascotChatRequest request, String fallback) {
        if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            return request.getClientRequestId().trim();
        }
        return fallback;
    }

    private void rejectDuplicateMascotBegin(AiCallBeginResult begin) {
        if (begin == null) {
            return;
        }
        if (begin.isDuplicateSuccess()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "该对话请求已处理，请勿重复提交"));
        }
        if (begin.isTerminalFailure()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "该对话请求已失败，请更换 clientRequestId"));
        }
    }

    private void reserveUsageQuota(User user, String skill, String route,
                                 boolean[] reservedQwenFlash, boolean[] reservedAdvanced) {
        if ("writing".equals(skill) || "chat".equals(skill) || "help".equals(skill)) {
            reserveAiQuota(user, route, reservedQwenFlash, reservedAdvanced);
        }
    }

    private AiImageResponseVO delegateMascotImage(
            User user,
            MascotChatRequest request,
            String imagePrompt,
            String sessionKey,
            Long dbSessionId) {
        if (!isVip(user)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_QUOTA, "生图功能仅向会员开放"));
        }
        AiImageRequest imageRequest = new AiImageRequest();
        imageRequest.setPrompt(imagePrompt);
        imageRequest.setQuality(resolveImageQuality(request, user));
        imageRequest.setSessionId(sessionKey);
        imageRequest.setEphemeral(true);
        imageRequest.setUsePointsBilling(Boolean.TRUE.equals(request.getUsePointsBilling()));
        imageRequest.setClientRequestId(imageRequestId(request, sessionKey));
        AiImageResponseVO image = aiCompanionApiService.image(user.getId(), imageRequest);
        if (dbSessionId != null && image.getUrl() != null && !image.getUrl().isBlank()) {
            companionMemoryService.appendImageMessage(dbSessionId, "assistant", image.getUrl(), imagePrompt);
        }
        return image;
    }

    private String resolveImageQuality(MascotChatRequest request, User user) {
        if ("premium".equalsIgnoreCase(request.getImageQuality()) && isVip(user)) {
            return "premium";
        }
        return "normal";
    }

    private String imageRequestId(MascotChatRequest request, String sessionKey) {
        if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            return request.getClientRequestId().trim() + ":image";
        }
        return "mascot-image-" + sessionKey + "-" + System.currentTimeMillis();
    }

    private boolean isImageAction(Map<String, Object> moduleData) {
        return "IMAGE".equalsIgnoreCase(String.valueOf(moduleData.get("action")));
    }

    private static Integer intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public MascotChatResponseVO chat(User user, MascotChatRequest request, String clientIp) {
        String skill = normalizeSkill(request);
        boolean ephemeral = Boolean.TRUE.equals(request.getEphemeral());

        boolean vip = isVip(user);
        boolean usePoints = Boolean.TRUE.equals(request.getUsePointsBilling());
        boolean reservedBasic = false;
        String route = normalizeLlmRoute(resolveLlmRoute(request, vip, skill, user));
        String fallbackModel = aiPointsBillingService.resolveModelFromRoute(route);

        if (usePoints) {
            aiPointsBillingService.ensureBalance(user,
                    aiPointsBillingService.estimatePoints(fallbackModel,
                            Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                            Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));
        } else if (!vip) {
            reserveBasicSlot(user.getId());
            reservedBasic = true;
        }

        boolean[] reservedQwenFlash = {false};
        boolean[] reservedAdvanced = {false};
        if (!usePoints) {
            try {
                reserveUsageQuota(user, skill, route, reservedQwenFlash, reservedAdvanced);
            } catch (ApplicationException ex) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                throw ex;
            }
        }

        Long dbSessionId = null;
        List<MascotHistoryTurn> mergedHistory;
        if (ephemeral) {
            mergedHistory = request.getHistory() != null ? request.getHistory() : List.of();
        } else {
            dbSessionId = companionMemoryService.ensureSession(user.getId(), skill, request.getSessionId());
            List<MascotHistoryTurn> dbHistory = companionMemoryService.loadHistoryTurns(dbSessionId, 16);
            mergedHistory = dbHistory.isEmpty() ? request.getHistory() : dbHistory;
        }

        String pySessionKey = ephemeral
                ? (request.getSessionId() != null && !request.getSessionId().isBlank()
                ? request.getSessionId().trim() : String.valueOf(user.getId()))
                : String.valueOf(dbSessionId);
        String billingRelatedId = billingRelatedId(request, pySessionKey);
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), featureCode(skill), request.getClientRequestId(), fallbackModel);
        rejectDuplicateMascotBegin(begin);
        long startMs = System.currentTimeMillis();

        Map<String, Object> pyBody = new HashMap<>();
        pyBody.put("message", request.getMessage().trim());
        pyBody.put("session_id", pySessionKey);
        pyBody.put("appearance", normalizeAppearanceForPy(request));
        pyBody.put("tier", vip ? "vip" : "basic");
        int vipTier = user.getVipTier() != null ? user.getVipTier().intValue() : 0;
        if (vip && vipTier <= 0) {
            vipTier = 1;
        }
        pyBody.put("vip_tier", vipTier);
        pyBody.put("skill", skill);
        pyBody.put("history", toPyHistory(mergedHistory));
        pyBody.put("llm_provider", route);
        if (request.getClientDatetime() != null && !request.getClientDatetime().isBlank()) {
            pyBody.put("client_datetime", request.getClientDatetime().trim());
        }
        if (isPublicClientIp(clientIp)) {
            pyBody.put("client_ip", clientIp.trim());
        }

        RestTemplate restTemplate = forumRestTemplate;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey != null && !internalKey.isBlank()) {
            headers.set("X-Internal-Key", internalKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(gatewayRequest(pyBody), headers);

        if (!ephemeral && dbSessionId != null) {
            companionMemoryService.appendTextMessage(dbSessionId, "user", request.getMessage().trim());
        }

        Map body;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(mascotAiUrl, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("bad status");
            }
            body = response.getBody();
        } catch (ApplicationException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw ex;
        } catch (Exception e) {
            log.warn("看板娘 Python 调用失败: {}", e.getMessage());
            aiCallRecordService.markFailure(begin, AiCallState.TIMEOUT, e.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI));
        }

        Object codeObj = body.get("code");
        int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : -1;
        if (code != 200) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, "mascot error code=" + code);
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            String msg = body.get("msg") != null ? String.valueOf(body.get("msg")) : "mascot error";
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, msg));
        }

        Map<String, Object> moduleData = gatewayModuleData(body);
        if (moduleData == null) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, "mascot gateway response invalid");
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI));
        }
        AiModelUsageDTO usage = parseUsage(body, fallbackModel);
        Map<String, Object> billing;
        try {
            billing = billMascotUsage(begin, user, skill, usage, billingRelatedId, usePoints,
                    System.currentTimeMillis() - startMs);
        } catch (ApplicationException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw ex;
        }

        String reply = moduleData.get("reply") != null ? String.valueOf(moduleData.get("reply")) : "";
        String imageUrl = "";
        if (isImageAction(moduleData)) {
            String imagePrompt = String.valueOf(moduleData.getOrDefault("imagePrompt", "")).trim();
            if (imagePrompt.isBlank()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "生图提示词不能为空"));
            }
            AiImageResponseVO image = delegateMascotImage(user, request, imagePrompt, pySessionKey, dbSessionId);
            imageUrl = image.getUrl();
        }
        if (!ephemeral && dbSessionId != null) {
            if (!reply.isBlank()) {
                companionMemoryService.appendTextMessage(dbSessionId, "assistant", reply,
                        parseImageGallery(moduleData.get("searchImageGallery")));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", pySessionKey);
        data.put("reply", reply);
        data.put("imageUrl", imageUrl);
        data.put("live2d", moduleData.get("live2d") instanceof Map ? moduleData.get("live2d") : Map.of());
        data.put("suggestedAppearance", moduleData.get("suggestedAppearance"));
        data.put("tier", vip ? "vip" : "basic");
        data.put("pointsCost", billing.get("pointsCost"));
        data.put("balanceAfter", billing.get("balanceAfter"));
        data.put("billingMode", billing.get("billingMode"));
        data.put("usageStats", billing.get("usageStats"));
        data.put("modelCode", usage.getModelCode());
        data.put("estimated", usage.getEstimated());
        data.put("relatedSearchOffer", Boolean.TRUE.equals(moduleData.get("relatedSearchOffer")));
        data.put("relatedSearchQuery", moduleData.get("relatedSearchQuery"));
        data.put("searchImageGallery", parseImageGallery(moduleData.get("searchImageGallery")));
        return MascotConverter.toChatResponse(data);
    }

    private String mascotStreamAiUrl() {
        if (mascotAiUrl == null || mascotAiUrl.isBlank()) {
            return "http://localhost:5000/api/v1/gateway/stream";
        }
        String u = mascotAiUrl.trim();
        return u.endsWith("/invoke") ? u.substring(0, u.length() - "/invoke".length()) + "/stream" : u;
    }

    private Map<String, Object> gatewayRequest(Map<String, Object> payload) {
        return gatewayRequest(payload, "CHAT");
    }

    private Map<String, Object> gatewayRequest(Map<String, Object> payload, String intent) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskType", "MASCOT");
        request.put("intent", intent);
        request.put("version", "v1");
        request.put("userContext", Collections.emptyMap());
        request.put("payload", payload);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> gatewayModuleData(Map body) {
        if (!(body.get("data") instanceof Map<?, ?> gateway)
                || !Boolean.TRUE.equals(gateway.get("success"))
                || !(gateway.get("data") instanceof Map<?, ?> data)) {
            return null;
        }
        Map<String, Object> normalized = new HashMap<>();
        data.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        Object usage = gateway.get("usage");
        body.put("usage", usage instanceof Map ? usage : Map.of());
        return normalized;
    }

    @Override
    public CompanionContextWindowVO getContextWindow(User user, Long sessionId) {
        return companionMemoryService.getContextWindow(user.getId(), sessionId);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public CompanionContextWindowVO compressContext(User user, Long sessionId) {
        List<MascotHistoryTurn> history = companionMemoryService.loadCompressibleHistory(user.getId(), sessionId);
        if (history.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "没有可压缩的会话内容"));
        }
        CompanionContextWindowVO window = companionMemoryService.getContextWindow(user.getId(), sessionId);
        boolean reservedFlash = false;
        try {
            aiQuotaService.consumeQwenFlash(user);
            reservedFlash = true;
            Map<String, Object> payload = new HashMap<>();
            payload.put("history", toPyHistory(history));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalKey != null && !internalKey.isBlank()) {
                headers.set("X-Internal-Key", internalKey);
            }
            ResponseEntity<Map> response = forumRestTemplate.postForEntity(
                    mascotAiUrl,
                    new HttpEntity<>(gatewayRequest(payload, "CONTEXT_COMPRESS"), headers),
                    Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("context compress gateway unavailable");
            }
            Map<String, Object> moduleData = gatewayModuleData(response.getBody());
            String summary = moduleData == null ? "" : String.valueOf(moduleData.getOrDefault("summary", "")).trim();
            if (summary.isBlank()) {
                throw new IllegalStateException("context summary empty");
            }
            companionMemoryService.appendContextSummary(user.getId(), sessionId, summary, window.getUsedTokens());
            return companionMemoryService.getContextWindow(user.getId(), sessionId);
        } catch (ApplicationException exception) {
            if (reservedFlash) {
                aiQuotaService.releaseQwenFlash(user);
            }
            throw exception;
        } catch (Exception exception) {
            if (reservedFlash) {
                aiQuotaService.releaseQwenFlash(user);
            }
            log.warn("看板娘上下文压缩失败: {}", exception.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "上下文压缩失败，请稍后重试"));
        }
    }

    private List<CompanionImageGalleryItemVO> parseImageGallery(Object raw) {
        if (!(raw instanceof List<?> rows)) {
            return List.of();
        }
        List<CompanionImageGalleryItemVO> gallery = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item)) {
                continue;
            }
            Object rawUrl = item.get("url");
            String url = rawUrl == null ? "" : String.valueOf(rawUrl).trim();
            if (!url.startsWith("https://") || url.length() > 2048 || !seenUrls.add(url)) {
                continue;
            }
            CompanionImageGalleryItemVO vo = new CompanionImageGalleryItemVO();
            vo.setUrl(url);
            Object rawTitle = item.get("title");
            Object rawSource = item.get("source");
            vo.setTitle(rawTitle == null ? "" : String.valueOf(rawTitle).trim());
            vo.setSource(rawSource == null ? "" : String.valueOf(rawSource).trim());
            gallery.add(vo);
            if (gallery.size() >= 5) {
                break;
            }
        }
        return gallery;
    }

    private boolean isPublicClientIp(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value.trim());
            return !(address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void sendMascotSse(SseEmitter emitter, Map<String, Object> payload) throws Exception {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    @Override
    public void streamChat(User user, MascotChatRequest request, String clientIp, SseEmitter emitter) {
        String skill = normalizeSkill(request);
        boolean ephemeral = Boolean.TRUE.equals(request.getEphemeral());
        boolean vip = isVip(user);
        boolean usePoints = Boolean.TRUE.equals(request.getUsePointsBilling());
        boolean reservedBasic = false;
        String route = normalizeLlmRoute(resolveLlmRoute(request, vip, skill, user));
        String fallbackModel = aiPointsBillingService.resolveModelFromRoute(route);

        if (usePoints) {
            try {
                aiPointsBillingService.ensureBalance(user,
                        aiPointsBillingService.estimatePoints(fallbackModel,
                                Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                                Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));
            } catch (ApplicationException ex) {
                try {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "balance"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(ex);
                }
                return;
            }
        } else if (!vip) {
            try {
                reserveBasicSlot(user.getId());
                reservedBasic = true;
            } catch (ApplicationException ex) {
                try {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "quota"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(ex);
                }
                return;
            }
        }

        boolean[] reservedQwenFlash = {false};
        boolean[] reservedAdvanced = {false};
        if (!usePoints) {
            try {
                reserveUsageQuota(user, skill, route, reservedQwenFlash, reservedAdvanced);
            } catch (ApplicationException ex) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                try {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "quota"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(ex);
                }
                return;
            }
        }

        Long dbSessionId = null;
        List<MascotHistoryTurn> mergedHistory;
        if (ephemeral) {
            mergedHistory = request.getHistory() != null ? request.getHistory() : List.of();
        } else {
            dbSessionId = companionMemoryService.ensureSession(user.getId(), skill, request.getSessionId());
            List<MascotHistoryTurn> dbHistory = companionMemoryService.loadHistoryTurns(dbSessionId, 16);
            mergedHistory = dbHistory.isEmpty()
                    ? (request.getHistory() != null ? request.getHistory() : List.of())
                    : dbHistory;
        }
        String pySessionKey = ephemeral
                ? (request.getSessionId() != null && !request.getSessionId().isBlank()
                ? request.getSessionId().trim() : String.valueOf(user.getId()))
                : String.valueOf(dbSessionId);
        final Long persistSessionId = dbSessionId;
        final String userMessage = request.getMessage().trim();
        final StringBuilder replyBuffer = new StringBuilder();
        final AtomicReference<List<CompanionImageGalleryItemVO>> streamSearchImageGallery = new AtomicReference<>(List.of());
        boolean imageRequested = false;
        String imagePrompt = "";
        final String billingRelatedId = billingRelatedId(request, pySessionKey);
        final AiCallBeginResult streamBegin = aiCallRecordService.beginCall(
                user.getId(), featureCode(skill), request.getClientRequestId(), fallbackModel);
        try {
            rejectDuplicateMascotBegin(streamBegin);
        } catch (ApplicationException ex) {
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            try {
                sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "duplicate"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(ex);
            }
            return;
        }
        final long streamStartMs = System.currentTimeMillis();

        if (!ephemeral && persistSessionId != null) {
            companionMemoryService.appendTextMessage(persistSessionId, "user", userMessage);
            try {
                sendMascotSse(emitter, Map.of("meta", Map.of("sessionId", String.valueOf(persistSessionId))));
            } catch (Exception ex) {
                log.warn("推送会话 id 失败: {}", ex.getMessage());
            }
        }

        Map<String, Object> pyBody = new HashMap<>();
        pyBody.put("message", userMessage);
        pyBody.put("session_id", pySessionKey);
        pyBody.put("appearance", normalizeAppearanceForPy(request));
        pyBody.put("tier", vip ? "vip" : "basic");
        int vipTier = user.getVipTier() != null ? user.getVipTier().intValue() : 0;
        if (vip && vipTier <= 0) {
            vipTier = 1;
        }
        pyBody.put("vip_tier", vipTier);
        pyBody.put("skill", skill);
        pyBody.put("history", toPyHistory(mergedHistory));
        pyBody.put("llm_provider", route);
        if (request.getClientDatetime() != null && !request.getClientDatetime().isBlank()) {
            pyBody.put("client_datetime", request.getClientDatetime().trim());
        }
        if (isPublicClientIp(clientIp)) {
            pyBody.put("client_ip", clientIp.trim());
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(mascotStreamAiUrl()).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (internalKey != null && !internalKey.isBlank()) {
                conn.setRequestProperty("X-Internal-Key", internalKey);
            }
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(180_000);
            byte[] body = objectMapper.writeValueAsBytes(gatewayRequest(pyBody));
            conn.getOutputStream().write(body);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
                aiCallRecordService.markFailure(streamBegin, AiCallState.TIMEOUT, "stream http " + code);
                persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageGallery.get());
                sendMascotSse(emitter, Map.of("error", "AI 服务暂时不可用"));
                emitter.complete();
                return;
            }

            AiModelUsageDTO usage = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = objectMapper.readValue(payload, Map.class);
                    if ("error".equals(chunk.get("type")) && chunk.get("data") instanceof Map<?, ?> errorData) {
                        Object message = errorData.get("message");
                        sendMascotSse(emitter, Map.of("error", message != null ? String.valueOf(message) : "AI 服务暂时不可用"));
                        break;
                    }
                    String eventType = String.valueOf(chunk.get("type"));
                    if ("progress".equals(eventType) && chunk.get("data") instanceof Map<?, ?> progressData) {
                        Map<String, Object> progressMeta = new HashMap<>();
                        progressData.forEach((key, value) -> progressMeta.put(String.valueOf(key), value));
                        sendMascotSse(emitter, Map.of("meta", progressMeta));
                        continue;
                    }
                    if ("text".equals(eventType) && chunk.get("data") instanceof Map<?, ?> textData) {
                        Object text = textData.get("text");
                        if (text != null && !String.valueOf(text).isEmpty()) {
                            String piece = String.valueOf(text);
                            replyBuffer.append(piece);
                            sendMascotSse(emitter, Map.of("text", piece));
                        }
                        continue;
                    }
                    if ("meta".equals(eventType) && chunk.get("data") instanceof Map<?, ?> eventMeta) {
                        Map<String, Object> metaMap = new HashMap<>();
                        eventMeta.forEach((key, value) -> metaMap.put(String.valueOf(key), value));
                        if (isImageAction(metaMap)) {
                            imageRequested = true;
                            imagePrompt = String.valueOf(metaMap.getOrDefault("imagePrompt", "")).trim();
                        }
                        List<CompanionImageGalleryItemVO> gallery = parseImageGallery(metaMap.get("searchImageGallery"));
                        if (!gallery.isEmpty()) {
                            streamSearchImageGallery.set(gallery);
                        }
                        sendMascotSse(emitter, Map.of("meta", metaMap));
                        continue;
                    }
                    if ("usage".equals(eventType) && chunk.get("data") instanceof Map<?, ?> eventUsage) {
                        Map<String, Object> usageMap = new HashMap<>();
                        eventUsage.forEach((key, value) -> usageMap.put(String.valueOf(key), value));
                        usage = parseUsage(Map.of("usage", usageMap), fallbackModel);
                        continue;
                    }
                    if ("final".equals(chunk.get("type")) && chunk.get("data") instanceof Map<?, ?> gateway) {
                        if (!(gateway.get("data") instanceof Map<?, ?> rawData)) {
                            continue;
                        }
                        Map<String, Object> finalData = new HashMap<>();
                        rawData.forEach((key, value) -> finalData.put(String.valueOf(key), value));
                        if (isImageAction(finalData)) {
                            imageRequested = true;
                            imagePrompt = String.valueOf(finalData.getOrDefault("imagePrompt", "")).trim();
                        }
                        if (Boolean.TRUE.equals(finalData.get("relatedSearchOffer"))
                                && finalData.get("relatedSearchQuery") != null) {
                            Map<String, Object> searchMeta = new LinkedHashMap<>();
                            searchMeta.put("relatedSearchOffer", true);
                            searchMeta.put("relatedSearchQuery", String.valueOf(finalData.get("relatedSearchQuery")));
                            searchMeta.put("complexity", String.valueOf(finalData.getOrDefault("complexity", "SIMPLE")));
                            sendMascotSse(emitter, Map.of("meta", searchMeta));
                        }
                        List<CompanionImageGalleryItemVO> gallery = parseImageGallery(finalData.get("searchImageGallery"));
                        if (!gallery.isEmpty()) {
                            streamSearchImageGallery.set(gallery);
                            sendMascotSse(emitter, Map.of("meta", Map.of("searchImageGallery", gallery)));
                        }
                        Object reply = finalData.get("reply");
                        if (reply != null && !String.valueOf(reply).isEmpty()) {
                            String text = String.valueOf(reply);
                            replyBuffer.append(text);
                            sendMascotSse(emitter, Map.of("text", text));
                        }
                        Object usageObj = gateway.get("usage");
                        if (usageObj instanceof Map<?, ?> usageMap) {
                            Map<String, Object> normalizedUsage = new HashMap<>();
                            usageMap.forEach((key, value) -> normalizedUsage.put(String.valueOf(key), value));
                            usage = parseUsage(Map.of("usage", normalizedUsage), fallbackModel);
                        }
                        continue;
                    }
                    if (chunk.get("error") != null) {
                        sendMascotSse(emitter, Map.of("error", String.valueOf(chunk.get("error"))));
                        break;
                    }
                    Object textObj = chunk.get("text");
                    if (textObj != null) {
                        String piece = String.valueOf(textObj);
                        if (!piece.isEmpty()) {
                            replyBuffer.append(piece);
                            sendMascotSse(emitter, Map.of("text", piece));
                        }
                    }
                    Object metaObj = chunk.get("meta");
                    if (metaObj instanceof Map<?, ?> mm) {
                        Map<String, Object> metaMap = new HashMap<>();
                        mm.forEach((k, v) -> metaMap.put(String.valueOf(k), v));
                        List<CompanionImageGalleryItemVO> gallery = parseImageGallery(metaMap.get("searchImageGallery"));
                        if (!gallery.isEmpty()) {
                            streamSearchImageGallery.set(gallery);
                        }
                        sendMascotSse(emitter, Map.of("meta", metaMap));
                    }
                    Object usageObj = chunk.get("usage");
                    if (usageObj instanceof Map<?, ?> um) {
                        Map<String, Object> usageMap = new HashMap<>();
                        um.forEach((k, v) -> usageMap.put(String.valueOf(k), v));
                        usage = parseUsage(Map.of("usage", usageMap), fallbackModel);
                    }
                }
            }

            if (usage == null) {
                usage = aiPointsBillingService.normalizeUsage(new AiModelUsageDTO(), fallbackModel);
                usage.setEstimated(true);
            }
            Map<String, Object> billing;
            try {
                billing = billMascotUsage(streamBegin, user, skill, usage, billingRelatedId, usePoints,
                        System.currentTimeMillis() - streamStartMs);
            } catch (ApplicationException ex) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
                aiCallRecordService.markFailure(streamBegin, AiCallState.FAILED, ex.getMessage());
                persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageGallery.get());
                sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "charge failed"));
                emitter.complete();
                return;
            }

            AiImageResponseVO delegatedImage = null;
            if (imageRequested) {
                if (imagePrompt.isBlank()) {
                    sendMascotSse(emitter, Map.of("error", "生图提示词不能为空"));
                    emitter.complete();
                    return;
                }
                sendMascotSse(emitter, Map.of("meta", Map.of("imageGenerating", true)));
                try {
                    delegatedImage = delegateMascotImage(user, request, imagePrompt, pySessionKey, persistSessionId);
                } catch (ApplicationException ex) {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "生图失败"));
                    emitter.complete();
                    return;
                }
                Map<String, Object> imageMeta = new LinkedHashMap<>();
                imageMeta.put("imageUrl", delegatedImage.getUrl());
                imageMeta.put("usageStats", delegatedImage.getUsageStats());
                imageMeta.put("pointsCost", delegatedImage.getPointsCost());
                imageMeta.put("balanceAfter", delegatedImage.getBalanceAfter());
                imageMeta.put("billingMode", delegatedImage.getBillingMode());
                sendMascotSse(emitter, Map.of("meta", imageMeta));
            }

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sessionId", pySessionKey);
            meta.put("pointsCost", billing.get("pointsCost"));
            meta.put("balanceAfter", billing.get("balanceAfter"));
            meta.put("billingMode", billing.get("billingMode"));
            meta.put("usageStats", billing.get("usageStats"));
            meta.put("modelCode", usage.getModelCode());
            meta.put("llmRoute", route);
            Long assistantMessageId = persistCompanionAssistantReply(
                    ephemeral, persistSessionId, replyBuffer, streamSearchImageGallery.get());
            if (assistantMessageId != null) {
                meta.put("assistantMessageId", assistantMessageId);
            }
            sendMascotSse(emitter, Map.of("meta", meta));
            emitter.complete();
        } catch (Exception e) {
            log.warn("看板娘流式调用失败: {}", e.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            if (replyBuffer.length() > 0) {
                aiCallRecordService.settlePartialOutput(
                        streamBegin, user, featureCode(skill), fallbackModel,
                        replyBuffer.length(), billingRelatedId,
                        Constant.POINTS_SOURCE_AI_COMPANION, usePoints);
            } else {
                aiCallRecordService.markFailure(streamBegin, AiCallState.DISCONNECTED, e.getMessage());
            }
            persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageGallery.get());
            try {
                sendMascotSse(emitter, Map.of("error", "对话失败，请稍后重试"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(e);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Long persistCompanionAssistantReply(
            boolean ephemeral, Long sessionId, CharSequence reply, List<CompanionImageGalleryItemVO> imageGallery) {
        if (ephemeral || sessionId == null || reply == null || reply.length() == 0) {
            return null;
        }
        return companionMemoryService.appendTextMessage(sessionId, "assistant", reply.toString(), imageGallery);
    }
}

