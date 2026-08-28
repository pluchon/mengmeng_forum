package org.pluchon.forum.service.impl.mascot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.constant.ForumBusinessConstants;
import org.pluchon.forum.common.AiCallState;
import org.pluchon.forum.common.MascotRelatedRecommendationState;
import org.pluchon.forum.common.MascotRelatedSelectionReason;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ForumMascotModel;
import org.pluchon.forum.entity.db.ForumCompanionSession;
import org.pluchon.forum.entity.db.ForumMascotRelatedRecommendation;
import org.pluchon.forum.entity.db.ForumMascotRelatedRecommendationItem;
import org.pluchon.forum.entity.db.ForumCompanionMessage;
import org.pluchon.forum.entity.db.UserMascotPreference;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;
import org.pluchon.forum.entity.dto.AiImageRequest;
import org.pluchon.forum.entity.dto.MascotChatRequest;
import org.pluchon.forum.entity.dto.MascotHistoryTurn;
import org.pluchon.forum.entity.dto.MascotMemoryEditRequest;
import org.pluchon.forum.entity.dto.MascotRelatedRecommendationRequest;
import org.pluchon.forum.converter.AiHubConverter;
import org.pluchon.forum.converter.MascotConverter;
import org.pluchon.forum.entity.vo.ai.AiCallBeginResult;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.MascotChatResponseVO;
import org.pluchon.forum.entity.vo.MascotModelPublicVO;
import org.pluchon.forum.entity.vo.MascotMemoryVO;
import org.pluchon.forum.entity.vo.MascotRelatedArticleCandidate;
import org.pluchon.forum.entity.vo.MascotRelatedRecommendationItemVO;
import org.pluchon.forum.entity.vo.MascotRelatedRecommendationVO;
import org.pluchon.forum.entity.vo.CompanionContextWindowVO;
import org.pluchon.forum.entity.vo.CompanionImageGalleryItemVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ForumMascotModelMapper;
import org.pluchon.forum.mapper.ForumCompanionSessionMapper;
import org.pluchon.forum.mapper.ForumCompanionMessageMapper;
import org.pluchon.forum.mapper.ForumMascotRelatedRecommendationItemMapper;
import org.pluchon.forum.mapper.ForumMascotRelatedRecommendationMapper;
import org.pluchon.forum.mapper.UserMascotPreferenceMapper;
import org.pluchon.forum.mapper.AiUsageDailyMapper;
import org.pluchon.forum.api.content.ArticleInternalVO;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.api.economy.VipTierSnapshotVO;
import org.pluchon.forum.cloud.feign.ArticleInternalFeignClient;
import org.pluchon.forum.cloud.feign.AiVipInternalFeignClient;
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
import org.pluchon.forum.service.security.AiUserContext;
import org.pluchon.forum.service.security.AiUserLookupService;
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
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private AiUserLookupService aiUserLookupService;

    @Resource
    private UserMascotPreferenceMapper userMascotPreferenceMapper;

    @Resource
    private AiUsageDailyMapper aiUsageDailyMapper;

    @Resource
    private AiVipInternalFeignClient vipInternalFeignClient;

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
        AiUserContext exists = aiUserLookupService.getById(userId);
        if (exists == null) {
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
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        List<AiUsageDaily> rows = aiUsageDailyMapper.selectPage(new Page<>(1, 1, false),
                Wrappers.lambdaQuery(AiUsageDaily.class)
                        .eq(AiUsageDaily::getUserId, userId)
                        .eq(AiUsageDaily::getUsageDate, today)
                        .eq(AiUsageDaily::getDeleteState, 0)).getRecords();
        int used = 0;
        if (!rows.isEmpty() && rows.get(0).getAdvancedLlmUsed() != null) {
            used = rows.get(0).getAdvancedLlmUsed();
        }
        int percent = Math.min(100, (int) Math.round(used * 100.0 / limit));
        vo.setPercent(percent);
        vo.setQuotaLabel("Qwen 深度写作");
        vo.setCanUsePointsPay(percent >= 95);
        return vo;
    }

    // 用户确认后的相关帖子检索；结果项与选择原因在同一事务内保存
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MascotRelatedRecommendationVO recommendRelatedArticles(
            AiUserContext user, MascotRelatedRecommendationRequest request) {
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
    public List<MascotRelatedRecommendationVO> listRelatedRecommendations(AiUserContext user, Long sessionId) {
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

        Map<Long, ArticleInternalVO> articlesById = new HashMap<>();
        if (!articleIds.isEmpty()) {
            List<ArticleInternalVO> vos = articleInternalFeignClient.listByIds(new ArrayList<>(articleIds));
            if (vos != null) {
                for (ArticleInternalVO vo : vos) {
                    if (vo == null || vo.getId() == null) {
                        continue;
                    }
                    // Feign listByIds 已排除逻辑删除；内部视图仍按 deleteState 兜底
                    if (vo.getDeleteState() != null && vo.getDeleteState() == 1) {
                        continue;
                    }
                    articlesById.put(vo.getId(), vo);
                }
            }
        }
        Set<Long> authorIds = new HashSet<>();
        for (ArticleInternalVO article : articlesById.values()) {
            authorIds.add(article.getUserId());
        }
        Map<Long, AiUserContext> usersById = authorIds.isEmpty() ? Map.of() : aiUserLookupService.loadActiveUsers(authorIds);

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
                ArticleInternalVO article = articlesById.get(savedItem.getArticleId());
                if (article == null) {
                    continue;
                }
                MascotRelatedRecommendationItemVO item = new MascotRelatedRecommendationItemVO();
                item.setArticle(toArticleBriefVO(article));
                AiUserContext author = usersById.get(article.getUserId());
                item.setAuthor(toUserBriefVO(author));
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
            ArticleInternalVO article = candidate.getArticle();
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
                .filter(candidate -> computeHotScore(candidate.getArticle()) > 0D)
                .sorted(Comparator.comparingDouble(
                        (MascotRelatedArticleCandidate candidate) -> computeHotScore(candidate.getArticle()))
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
        Map<Long, AiUserContext> usersById = userIds.isEmpty() ? Map.of() : aiUserLookupService.loadActiveUsers(userIds);
        List<MascotRelatedRecommendationItemVO> items = new ArrayList<>();
        for (RelatedArticleSelection selection : selections) {
            ArticleInternalVO article = selection.candidate().getArticle();
            MascotRelatedRecommendationItemVO item = new MascotRelatedRecommendationItemVO();
            item.setArticle(toArticleBriefVO(article));
            AiUserContext author = usersById.get(article.getUserId());
            item.setAuthor(toUserBriefVO(author));
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

    private UserBriefVO toUserBriefVO(AiUserContext user) {
        if (user == null) {
            return null;
        }
        UserBriefVO vo = new UserBriefVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setIsAdmin(user.getIsAdmin());
        vo.setVipTier(user.getVipTier());
        vo.setVipExpireAt(user.getVipExpireAt());
        return vo;
    }

    private ArticleBriefVO toArticleBriefVO(ArticleInternalVO article) {
        if (article == null) {
            return null;
        }
        ArticleBriefVO vo = new ArticleBriefVO();
        vo.setId(article.getId());
        vo.setBoardId(article.getBoardId());
        vo.setUserId(article.getUserId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setVisitCount(article.getVisitCount());
        vo.setReplyCount(article.getReplyCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setFavoriteCount(article.getFavoriteCount());
        vo.setCoverImg(article.getCoverImg());
        vo.setMediaType(article.getMediaType());
        vo.setVideoUrl(article.getVideoUrl());
        vo.setArticleType(article.getArticleType());
        vo.setQuestionStatus(article.getQuestionStatus());
        vo.setAcceptedReplyId(article.getAcceptedReplyId());
        vo.setStatus(article.getStatus());
        vo.setState(article.getState());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    private double computeHotScore(ArticleInternalVO article) {
        if (article == null || article.getCreateTime() == null) {
            return 0D;
        }
        long ageHours = Duration.between(article.getCreateTime().toInstant(), Instant.now()).toHours();
        if (ageHours > ForumBusinessConstants.HOT_RANK_WINDOW_DAYS * 24L) {
            return 0D;
        }
        int like = article.getLikeCount() == null ? 0 : article.getLikeCount();
        int visit = article.getVisitCount() == null ? 0 : article.getVisitCount();
        int favorite = article.getFavoriteCount() == null ? 0 : article.getFavoriteCount();
        int reply = article.getReplyCount() == null ? 0 : article.getReplyCount();
        int subReply = article.getSubReplyCount() == null ? 0 : article.getSubReplyCount();
        double base = like * Constant.HOT_SCORE_WEIGHT_LIKE
                + visit * Constant.HOT_SCORE_WEIGHT_VISIT
                + favorite * Constant.HOT_SCORE_WEIGHT_FAVORITE
                + (reply + subReply) * Constant.HOT_SCORE_WEIGHT_REPLY;
        double decay = 1D / (1D + ageHours / 24D);
        double boost = ageHours <= ForumBusinessConstants.HOT_RANK_NEW_POST_HOURS
                ? ForumBusinessConstants.HOT_RANK_NEW_POST_BOOST : 1D;
        return base * decay * boost;
    }

    private boolean isVip(AiUserContext user) {
        if (user.isVipActive()) {
            return true;
        }
        if (treatAdminAsVip) {
            return user.getIsAdmin() != null && user.getIsAdmin() == 1;
        }
        return false;
    }

    private String quotaKey(Long userId) {
        String day = LocalDate.now(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.BASIC_ISO_DATE);
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

    private int effectiveVipTier(AiUserContext user) {
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

    private String resolveLlmRoute(MascotChatRequest request, boolean vip, String skill, AiUserContext user) {
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

    private void reserveAiQuota(AiUserContext user, String route, boolean[] reservedQwenFlash, boolean[] reservedAdvanced) {
        if (route.startsWith("qwen-deep")) {
            aiQuotaService.consumeAdvancedLlm(user);
            reservedAdvanced[0] = true;
        } else {
            aiQuotaService.consumeQwenFlash(user);
            reservedQwenFlash[0] = true;
        }
    }

    private void releaseAiQuota(AiUserContext user, boolean reservedQwenFlash, boolean reservedAdvanced) {
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
        return "xiaomeng";
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

    private Map<String, Object> billMascotUsage(AiCallBeginResult begin, AiUserContext user, String skill,
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
        // 禁止回落到会话级 key：否则同会话后续消息会撞到同一 ai_bill 幂等键而漏扣
        return "mascot-" + UUID.randomUUID().toString().replace("-", "");
    }

    private void rejectDuplicateMascotBegin(AiCallBeginResult begin) {
        if (begin == null) {
            return;
        }
        if (begin.isDuplicateSuccess()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "这条消息已经发送过了，请不要重复提交"));
        }
        if (begin.isTerminalFailure()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "这条消息发送失败了，请重新发送"));
        }
    }

    private void reserveUsageQuota(AiUserContext user, String skill, String route,
                                 boolean[] reservedQwenFlash, boolean[] reservedAdvanced) {
        if ("writing".equals(skill) || "chat".equals(skill) || "help".equals(skill)) {
            reserveAiQuota(user, route, reservedQwenFlash, reservedAdvanced);
        }
    }

    private boolean shouldAutoFallbackToPoints(ApplicationException ex) {
        return ex != null
                && ex.getErrorResult() != null
                && ex.getErrorResult().getCode() == ResultCode.FAILED_AI_QUOTA_EXCEEDED.getCode();
    }

    private HttpHeaders internalJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey != null && !internalKey.isBlank()) {
            headers.set("X-Internal-Key", internalKey);
        }
        return headers;
    }

    private List<String> stringList(Object raw, int limit) {
        List<String> out = new ArrayList<>();
        if (!(raw instanceof List<?> rows)) {
            return out;
        }
        for (Object row : rows) {
            String text = row == null ? "" : String.valueOf(row).trim();
            if (text.isBlank() || out.contains(text)) {
                continue;
            }
            out.add(text.substring(0, Math.min(40, text.length())));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private MascotMemoryVO loadMascotMemory(Long userId) {
        try {
            return companionMemoryService.getMascotMemory(userId);
        } catch (Exception ex) {
            log.warn("读取看板娘长期记忆失败 userId={}: {}", userId, ex.getMessage());
            MascotMemoryVO vo = new MascotMemoryVO();
            vo.setSummary("");
            vo.setFacts(List.of());
            vo.setUpdatedAt("");
            return vo;
        }
    }

    private List<String> loadLikedTitles(Long userId) {
        try {
            return articleInternalFeignClient.listLikedTitles(userId, 6);
        } catch (Exception ex) {
            log.warn("读取点赞帖子标题失败 userId={}", userId, ex);
            return List.of();
        }
    }

    private List<String> loadFavoriteSongs(Long userId) {
        try {
            return articleInternalFeignClient.listFavoriteSongTitles(userId, 6);
        } catch (Exception ex) {
            log.warn("读取收藏歌曲失败 userId={}", userId, ex);
            return List.of();
        }
    }

    private AiImageResponseVO delegateMascotImage(
            AiUserContext user,
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

    private String resolveImageQuality(MascotChatRequest request, AiUserContext user) {
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
    public MascotChatResponseVO chat(AiUserContext user, MascotChatRequest request, String clientIp) {
        String skill = normalizeSkill(request);
        boolean ephemeral = Boolean.TRUE.equals(request.getEphemeral());

        boolean vip = isVip(user);
        boolean usePoints = Boolean.TRUE.equals(request.getUsePointsBilling());
        boolean autoPointsFallback = false;
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
                if (shouldAutoFallbackToPoints(ex)) {
                    usePoints = true;
                    autoPointsFallback = true;
                    if (reservedBasic) {
                        releaseBasicSlot(user.getId());
                        reservedBasic = false;
                    }
                    aiPointsBillingService.ensureBalance(user,
                            aiPointsBillingService.estimatePoints(fallbackModel,
                                    Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                                    Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));
                } else {
                    if (reservedBasic) {
                        releaseBasicSlot(user.getId());
                    }
                    throw ex;
                }
            }
        }

        MascotMemoryVO mascotMemory = loadMascotMemory(user.getId());
        List<String> likedTitles = loadLikedTitles(user.getId());
        List<String> favoriteSongs = loadFavoriteSongs(user.getId());

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
        pyBody.put("memory_summary", mascotMemory.getSummary());
        pyBody.put("memory_facts", mascotMemory.getFacts());
        pyBody.put("liked_titles", likedTitles);
        pyBody.put("favorite_songs", favoriteSongs);
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
        List<AiModelUsageDTO> usageItems = AiHubConverter.toUsageItems(body.get("usage"));
        AiModelUsageDTO usage = usageItems.isEmpty()
                ? parseUsage(body, fallbackModel)
                : aiPointsBillingService.aggregateUsage(usageItems, fallbackModel);
        Map<String, Object> billing;
        try {
            billing = usageItems.size() > 1
                    ? aiCallRecordService.settleSuccessBatch(
                    begin, user, featureCode(skill), usageItems, fallbackModel,
                    billingRelatedId, Constant.POINTS_SOURCE_AI_COMPANION, usePoints,
                    System.currentTimeMillis() - startMs)
                    : billMascotUsage(begin, user, skill, usage, billingRelatedId, usePoints,
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

        autoSaveMemoryWrite(user.getId(), moduleData.get("memoryWrite"));

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
        data.put("askConfirmOffer", moduleData.get("askConfirmOffer"));
        data.put("searchImageGallery", parseImageGallery(moduleData.get("searchImageGallery")));
        data.put("quotaFallbackToPoints", autoPointsFallback);
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
    public CompanionContextWindowVO getContextWindow(AiUserContext user, Long sessionId) {
        return companionMemoryService.getContextWindow(user.getId(), sessionId);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public CompanionContextWindowVO compressContext(AiUserContext user, Long sessionId) {
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
            AiModelUsageDTO usage = parseUsage(response.getBody(), "qwen3.7-flash");
            aiPointsBillingService.bill(
                    user,
                    "companion_context_compress",
                    usage,
                    "context-compress:" + sessionId + ":" + System.currentTimeMillis(),
                    Constant.POINTS_SOURCE_AI_COMPANION);
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

    @Override
    public MascotMemoryVO getMascotMemory(AiUserContext user) {
        return companionMemoryService.getMascotMemory(user.getId());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public MascotMemoryVO editMascotMemory(AiUserContext user, MascotMemoryEditRequest request) {
        MascotMemoryVO current = companionMemoryService.getMascotMemory(user.getId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("memory_summary", current.getSummary());
        payload.put("memory_facts", current.getFacts());
        payload.put("memory_edit_instruction", request.getInstruction().trim());
        ResponseEntity<Map> response = forumRestTemplate.postForEntity(
                mascotAiUrl,
                new HttpEntity<>(gatewayRequest(payload, "MEMORY_EDIT"), internalJsonHeaders()),
                Map.class);
        Map<String, Object> body = response.getBody();
        Map<String, Object> data = gatewayModuleData(body);
        if (data == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "记忆更新失败"));
        }
        String summary = data.get("summary") == null ? "" : String.valueOf(data.get("summary")).trim();
        List<String> facts = stringList(data.get("facts"), 10);
        companionMemoryService.saveMascotMemory(user.getId(), summary, facts);
        return companionMemoryService.getMascotMemory(user.getId());
    }

    @SuppressWarnings("unchecked")
    private void autoSaveMemoryWrite(Long userId, Object memoryWriteRaw) {
        if (!(memoryWriteRaw instanceof Map<?, ?> mw) || mw.isEmpty()) {
            return;
        }
        try {
            String summary = mw.get("summary") == null ? "" : String.valueOf(mw.get("summary")).trim();
            List<String> facts = stringList(mw.get("facts"), 10);
            if (summary.isEmpty() && facts.isEmpty()) {
                return;
            }
            companionMemoryService.saveMascotMemory(userId, summary, facts);
        } catch (Exception ex) {
            log.warn("自动保存看板娘记忆失败 userId={}: {}", userId, ex.getMessage());
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
            String title = rawTitle == null ? "" : String.valueOf(rawTitle).trim();
            vo.setTitle(title.substring(0, Math.min(10, title.length())));
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
    public void streamChat(AiUserContext user, MascotChatRequest request, String clientIp, SseEmitter emitter) {
        String skill = normalizeSkill(request);
        boolean ephemeral = Boolean.TRUE.equals(request.getEphemeral());
        boolean vip = isVip(user);
        boolean usePoints = Boolean.TRUE.equals(request.getUsePointsBilling());
        boolean autoPointsFallback = false;
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
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null
                            ? ex.getMessage() : ResultCode.FAILED_POINTS_NOT_ENOUGH.getMessage()));
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
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null
                            ? ex.getMessage() : ResultCode.FAILED_MASCOT_QUOTA.getMessage()));
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
                if (shouldAutoFallbackToPoints(ex)) {
                    usePoints = true;
                    autoPointsFallback = true;
                    if (reservedBasic) {
                        releaseBasicSlot(user.getId());
                        reservedBasic = false;
                    }
                    try {
                        aiPointsBillingService.ensureBalance(user,
                                aiPointsBillingService.estimatePoints(fallbackModel,
                                        Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                                        Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));
                    } catch (ApplicationException balanceEx) {
                        try {
                            sendMascotSse(emitter, Map.of("error", balanceEx.getMessage() != null
                                    ? balanceEx.getMessage() : ResultCode.FAILED_POINTS_NOT_ENOUGH.getMessage()));
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(balanceEx);
                        }
                        return;
                    }
                } else {
                    if (reservedBasic) {
                        releaseBasicSlot(user.getId());
                    }
                    try {
                        sendMascotSse(emitter, Map.of("error", ex.getMessage() != null
                                ? ex.getMessage() : ResultCode.FAILED_MASCOT_QUOTA.getMessage()));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(ex);
                    }
                    return;
                }
            }
        }

        MascotMemoryVO mascotMemory = loadMascotMemory(user.getId());
        List<String> likedTitles = loadLikedTitles(user.getId());
        List<String> favoriteSongs = loadFavoriteSongs(user.getId());

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
        final AtomicReference<Object> streamMemoryWrite = new AtomicReference<>(null);
        boolean imageRequested = false;
        String imagePrompt = "";
        final String billingRelatedId = billingRelatedId(request, pySessionKey);
        final AtomicBoolean terminalHandled = new AtomicBoolean(false);
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
                sendMascotSse(emitter, Map.of("error", ex.getMessage() != null
                        ? ex.getMessage() : "这条消息已经发送过了，请不要重复提交"));
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
        pyBody.put("memory_summary", mascotMemory.getSummary());
        pyBody.put("memory_facts", mascotMemory.getFacts());
        pyBody.put("liked_titles", likedTitles);
        pyBody.put("favorite_songs", favoriteSongs);
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
            List<AiModelUsageDTO> usageItems = List.of();
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
                        log.warn("看板娘流式下游返回错误事件 type=error");
                        sendMascotSse(emitter, Map.of("error", "AI 服务暂时不可用，请稍后重试"));
                        break;
                    }
                    String eventType = String.valueOf(chunk.get("type"));
                    if ("progress".equals(eventType) && chunk.get("data") instanceof Map<?, ?> progressData) {
                        Map<String, Object> progressMeta = new HashMap<>();
                        progressData.forEach((key, value) -> {
                            // 链路编号只用于服务端排障，不下发给用户
                            if (!"traceId".equals(String.valueOf(key))) {
                                progressMeta.put(String.valueOf(key), value);
                            }
                        });
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
                        if (metaMap.containsKey("memoryWrite")) {
                            streamMemoryWrite.set(metaMap.remove("memoryWrite"));
                        }
                        sendMascotSse(emitter, Map.of("meta", metaMap));
                        continue;
                    }
                    if ("usage".equals(eventType) && chunk.get("data") instanceof Map<?, ?> eventUsage) {
                        Map<String, Object> usageMap = new HashMap<>();
                        eventUsage.forEach((key, value) -> usageMap.put(String.valueOf(key), value));
                        usageItems = AiHubConverter.toUsageItems(usageMap);
                        usage = usageItems.isEmpty()
                                ? parseUsage(Map.of("usage", usageMap), fallbackModel)
                                : aiPointsBillingService.aggregateUsage(usageItems, fallbackModel);
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
                        if (finalData.get("askConfirmOffer") instanceof Map<?, ?> askOfferRaw) {
                            Map<String, Object> askMeta = new LinkedHashMap<>();
                            Map<String, Object> normalizedOffer = new LinkedHashMap<>();
                            askOfferRaw.forEach((k, v) -> normalizedOffer.put(String.valueOf(k), v));
                            if (normalizedOffer.get("questions") instanceof List<?> questions && !questions.isEmpty()) {
                                askMeta.put("askConfirmOffer", normalizedOffer);
                                sendMascotSse(emitter, Map.of("meta", askMeta));
                            }
                        } else if (finalData.get("drawConfirmOffer") instanceof Map<?, ?> drawOfferRaw) {
                            // 兼容旧字段：单题生图确认 → 多题 ask
                            Map<String, Object> normalizedOffer = new LinkedHashMap<>();
                            drawOfferRaw.forEach((k, v) -> normalizedOffer.put(String.valueOf(k), v));
                            Object options = normalizedOffer.get("options");
                            Object question = normalizedOffer.get("question");
                            if (options instanceof List<?> list && !list.isEmpty() && question != null) {
                                Map<String, Object> q1 = new LinkedHashMap<>();
                                q1.put("id", "q1");
                                q1.put("question", String.valueOf(question));
                                q1.put("options", list);
                                Map<String, Object> ask = new LinkedHashMap<>();
                                ask.put("purpose", "draw");
                                ask.put("questions", List.of(q1));
                                sendMascotSse(emitter, Map.of("meta", Map.of("askConfirmOffer", ask)));
                            }
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
                            usageItems = AiHubConverter.toUsageItems(normalizedUsage);
                            usage = usageItems.isEmpty()
                                    ? parseUsage(Map.of("usage", normalizedUsage), fallbackModel)
                                    : aiPointsBillingService.aggregateUsage(usageItems, fallbackModel);
                        }
                        continue;
                    }
                    if (chunk.get("error") != null) {
                        log.warn("看板娘流式下游返回错误字段");
                        sendMascotSse(emitter, Map.of("error", "AI 服务暂时不可用，请稍后重试"));
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
                        usageItems = AiHubConverter.toUsageItems(usageMap);
                        usage = usageItems.isEmpty()
                                ? parseUsage(Map.of("usage", usageMap), fallbackModel)
                                : aiPointsBillingService.aggregateUsage(usageItems, fallbackModel);
                    }
                }
            }

            if (usage == null) {
                usage = aiPointsBillingService.normalizeUsage(new AiModelUsageDTO(), fallbackModel);
                usage.setEstimated(true);
            }
            Map<String, Object> billing;
            try {
                billing = usageItems.size() > 1
                        ? aiCallRecordService.settleSuccessBatch(
                        streamBegin, user, featureCode(skill), usageItems, fallbackModel,
                        billingRelatedId, Constant.POINTS_SOURCE_AI_COMPANION, usePoints,
                        System.currentTimeMillis() - streamStartMs)
                        : billMascotUsage(streamBegin, user, skill, usage, billingRelatedId, usePoints,
                        System.currentTimeMillis() - streamStartMs);
                terminalHandled.set(true);
            } catch (ApplicationException ex) {
                if (!terminalHandled.compareAndSet(false, true)) {
                    return;
                }
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
                aiCallRecordService.markFailure(streamBegin, AiCallState.FAILED, ex.getMessage());
                persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageGallery.get());
                sendMascotSse(emitter, Map.of("error", ex.getMessage() != null
                        ? ex.getMessage() : "扣费失败，请稍后再试"));
                emitter.complete();
                return;
            }

            autoSaveMemoryWrite(user.getId(), streamMemoryWrite.get());

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
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null
                            ? ex.getMessage() : "生成图片失败，请稍后再试"));
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
            meta.put("quotaFallbackToPoints", autoPointsFallback);
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
            if (!terminalHandled.compareAndSet(false, true)) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // ignore
                }
                return;
            }
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            if (!replyBuffer.isEmpty()) {
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
        if (ephemeral || sessionId == null || reply == null || reply.isEmpty()) {
            return null;
        }
        return companionMemoryService.appendTextMessage(sessionId, "assistant", reply.toString(), imageGallery);
    }
}

