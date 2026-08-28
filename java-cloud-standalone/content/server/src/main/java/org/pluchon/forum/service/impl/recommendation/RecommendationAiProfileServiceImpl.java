package org.pluchon.forum.service.impl.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleFavorite;
import org.pluchon.forum.entity.db.ArticleLike;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.ForumArticleAiFeature;
import org.pluchon.forum.entity.db.ForumUserAiProfileSnapshot;
import org.pluchon.forum.entity.db.UserRecommendFeedback;
import org.pluchon.forum.entity.dto.AiRecommendationArticleFeatureRequest;
import org.pluchon.forum.entity.dto.AiRecommendationProfileRequest;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.mapper.ArticleFavoriteMapper;
import org.pluchon.forum.mapper.ArticleLikeMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.ForumArticleAiFeatureMapper;
import org.pluchon.forum.mapper.ForumUserAiProfileSnapshotMapper;
import org.pluchon.forum.mapper.UserRecommendFeedbackMapper;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationAiProfileService;
import org.pluchon.forum.service.interfaces.recommendation.UserRecommendationSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

// 推荐 AI 特征与画像生成，失败时不影响发布和推荐主链路
@Slf4j
@Service
public class RecommendationAiProfileServiceImpl implements RecommendationAiProfileService {

    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_ENABLED = 0;
    private static final long SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final long FOURTEEN_DAYS_MILLIS = 14L * 24 * 60 * 60 * 1000;
    private static final String FEATURE_VERSION = "v1";

    @Autowired
    private ContentAiGatewayService aiHubService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private ArticleFavoriteMapper articleFavoriteMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    private UserRecommendFeedbackMapper feedbackMapper;

    @Autowired
    private ForumArticleAiFeatureMapper articleFeatureMapper;

    @Autowired
    private ForumUserAiProfileSnapshotMapper profileSnapshotMapper;

    @Autowired
    private UserRecommendationSettingService userRecommendationSettingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("recommendationExecutor")
    private Executor recommendationExecutor;

    @Override
    public void generateArticleFeature(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        executeAfterCommit(() -> recommendationExecutor.execute(() -> generateArticleFeatureAsync(articleId)));
    }

    private void generateArticleFeatureAsync(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        try {
            Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .eq(Article::getDeleteState, DELETE_FALSE)
                    .eq(Article::getState, STATE_ENABLED)
                    .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
            if (article == null) {
                return;
            }
            String contentHash = sha256(article.getTitle() + "\n" + article.getContent());
            ForumArticleAiFeature existing = articleFeatureMapper.selectOne(new LambdaQueryWrapper<ForumArticleAiFeature>()
                    .eq(ForumArticleAiFeature::getArticleId, articleId));
            if (existing != null && DELETE_FALSE == existing.getDeleteState()
                    && contentHash.equals(existing.getContentHash())
                    && existing.getFeatureJson() != null
                    && !"{}".equals(existing.getFeatureJson().trim())) {
                return;
            }
            AiRecommendationArticleFeatureRequest request = new AiRecommendationArticleFeatureRequest();
            request.setArticleId(articleId);
            request.setTitle(article.getTitle());
            request.setContent(article.getContent());
            Board board = boardMapper.selectById(article.getBoardId());
            request.setBoardName(board != null ? board.getName() : "");
            AiRecommendationFeatureResultVO result = aiHubService.generateRecommendationArticleFeature(request);
            if (result == null || result.getTopics() == null || result.getTopics().isEmpty()) {
                return;
            }
            String featureJson = objectMapper.writeValueAsString(Map.of(
                    "topics", result.getTopics(),
                    "summary", result.getSummary() == null ? "" : result.getSummary()));
            upsertArticleFeature(existing, articleId, featureJson, contentHash, result.getFeatureVersion(), result.getGeneratedBy());
        } catch (Exception e) {
            log.warn("推荐帖子特征生成失败 articleId={}: {}", articleId, e.getMessage());
        }
    }

    @Override
    public void requestProfileRefresh(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        ForumUserAiProfileSnapshot snapshot = findProfileSnapshot(userId);
        Date now = new Date();
        if (snapshot == null) {
            snapshot = new ForumUserAiProfileSnapshot();
            snapshot.setUserId(userId);
            snapshot.setProfileVersion(0L);
            snapshot.setFeatureVersion(FEATURE_VERSION);
            snapshot.setProfileJson("{}");
            snapshot.setGeneratedBy("PENDING");
            snapshot.setDeleteState(DELETE_FALSE);
            snapshot.setRefreshAfter(now);
            profileSnapshotMapper.insert(snapshot);
        } else {
            profileSnapshotMapper.update(null, new LambdaUpdateWrapper<ForumUserAiProfileSnapshot>()
                    .eq(ForumUserAiProfileSnapshot::getId, snapshot.getId())
                    .set(ForumUserAiProfileSnapshot::getDeleteState, DELETE_FALSE)
                    .set(ForumUserAiProfileSnapshot::getRefreshAfter, now));
        }
        executeAfterCommit(() -> recommendationExecutor.execute(() -> refreshProfile(userId)));
    }

    @Override
    public void refreshDueProfiles() {
        Date now = new Date();
        List<ForumUserAiProfileSnapshot> due = profileSnapshotMapper.selectList(
                new LambdaQueryWrapper<ForumUserAiProfileSnapshot>()
                        .eq(ForumUserAiProfileSnapshot::getDeleteState, DELETE_FALSE)
                        .le(ForumUserAiProfileSnapshot::getRefreshAfter, now)
                        .last("LIMIT 50"));
        for (ForumUserAiProfileSnapshot snapshot : due) {
            recommendationExecutor.execute(() -> refreshProfile(snapshot.getUserId()));
        }
    }

    @Override
    public Map<String, Double> getActiveTopicWeights(Long userId) {
        return getProfileTopicWeights(userId, "topics");
    }

    @Override
    public Map<String, Double> getAvoidTopicWeights(Long userId) {
        return getProfileTopicWeights(userId, "avoidTopics");
    }

    @Override
    public String getPreferenceQuery(Long userId) {
        ForumUserAiProfileSnapshot snapshot = findProfileSnapshot(userId);
        if (snapshot == null || snapshot.getProfileJson() == null || snapshot.getProfileJson().isBlank()) {
            return "";
        }
        try {
            Map<String, Object> profile = objectMapper.readValue(snapshot.getProfileJson(), new TypeReference<>() { });
            Object raw = profile.get("preferenceQuery");
            if (raw == null) {
                return "";
            }
            String query = String.valueOf(raw).trim();
            return query.length() > 200 ? query.substring(0, 200) : query;
        } catch (Exception e) {
            log.warn("用户偏好查询句解析失败 userId={}", userId);
            return "";
        }
    }

    private Map<String, Double> getProfileTopicWeights(Long userId, String fieldName) {
        ForumUserAiProfileSnapshot snapshot = findProfileSnapshot(userId);
        if (snapshot == null || snapshot.getProfileJson() == null || snapshot.getProfileJson().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> profile = objectMapper.readValue(snapshot.getProfileJson(), new TypeReference<>() { });
            return normalizedTopicWeights(profile.get(fieldName));
        } catch (Exception e) {
            log.warn("用户画像解析失败 userId={}", userId);
            return Map.of();
        }
    }

    private void refreshProfile(Long userId) {
        try {
            SignalWindow window = collectSignals(userId);
            List<String> explicitBoards = userRecommendationSettingService.getInterestBoardNames(userId);
            if (explicitBoards.isEmpty() && window.recent7().isEmpty() && window.recent14().isEmpty()
                    && window.negativeRecent7().isEmpty() && window.negativeRecent14().isEmpty()) {
                return;
            }
            AiRecommendationProfileRequest request = new AiRecommendationProfileRequest();
            request.setExplicitBoards(explicitBoards);
            request.setRecent7(window.recent7());
            request.setRecent14(window.recent14());
            request.setNegativeRecent7(window.negativeRecent7());
            request.setNegativeRecent14(window.negativeRecent14());
            AiRecommendationProfileResultVO result = aiHubService.generateRecommendationProfile(userId, request);
            if (result == null || ((result.getTopics() == null || result.getTopics().isEmpty())
                    && (result.getAvoidTopics() == null || result.getAvoidTopics().isEmpty())
                    && (result.getPreferenceQuery() == null || result.getPreferenceQuery().isBlank()))) {
                return;
            }
            Date now = new Date();
            ForumUserAiProfileSnapshot snapshot = findProfileSnapshot(userId);
            Map<String, Object> profilePayload = new HashMap<>();
            profilePayload.put("topics", result.getTopics() == null ? List.of() : result.getTopics());
            profilePayload.put("avoidTopics", result.getAvoidTopics() == null ? List.of() : result.getAvoidTopics());
            profilePayload.put("summary", result.getSummary() == null ? "" : result.getSummary());
            profilePayload.put("preferenceQuery", result.getPreferenceQuery() == null ? "" : result.getPreferenceQuery().trim());
            String profileJson = objectMapper.writeValueAsString(profilePayload);
            upsertProfile(snapshot, userId, profileJson, result, now);
        } catch (Exception e) {
            log.warn("推荐用户画像生成失败 userId={}: {}", userId, e.getMessage());
            deferProfileRefresh(userId);
        }
    }

    private void upsertArticleFeature(ForumArticleAiFeature existing, Long articleId, String featureJson,
            String contentHash, String featureVersion, String generatedBy) {
        if (existing == null) {
            ForumArticleAiFeature feature = new ForumArticleAiFeature();
            feature.setArticleId(articleId);
            feature.setFeatureJson(featureJson);
            feature.setContentHash(contentHash);
            feature.setFeatureVersion(featureVersion == null ? FEATURE_VERSION : featureVersion);
            feature.setGeneratedBy(generatedBy == null ? "AI" : generatedBy);
            feature.setDeleteState(DELETE_FALSE);
            articleFeatureMapper.insert(feature);
            return;
        }
        articleFeatureMapper.update(null, new LambdaUpdateWrapper<ForumArticleAiFeature>()
                .eq(ForumArticleAiFeature::getId, existing.getId())
                .set(ForumArticleAiFeature::getFeatureJson, featureJson)
                .set(ForumArticleAiFeature::getContentHash, contentHash)
                .set(ForumArticleAiFeature::getFeatureVersion, featureVersion == null ? FEATURE_VERSION : featureVersion)
                .set(ForumArticleAiFeature::getGeneratedBy, generatedBy == null ? "AI" : generatedBy)
                .set(ForumArticleAiFeature::getDeleteState, DELETE_FALSE));
    }

    private void upsertProfile(ForumUserAiProfileSnapshot snapshot, Long userId, String profileJson,
            AiRecommendationProfileResultVO result, Date now) {
        Date start = new Date(now.getTime() - FOURTEEN_DAYS_MILLIS);
        Date refreshAfter = new Date(now.getTime() + SEVEN_DAYS_MILLIS);
        if (snapshot == null) {
            ForumUserAiProfileSnapshot created = new ForumUserAiProfileSnapshot();
            created.setUserId(userId);
            created.setProfileVersion(1L);
            created.setProfileJson(profileJson);
            created.setFeatureVersion(result.getFeatureVersion() == null ? FEATURE_VERSION : result.getFeatureVersion());
            created.setSourceWindowStart(start);
            created.setSourceWindowEnd(now);
            created.setRefreshAfter(refreshAfter);
            created.setGeneratedBy(result.getGeneratedBy() == null ? "AI" : result.getGeneratedBy());
            created.setDeleteState(DELETE_FALSE);
            profileSnapshotMapper.insert(created);
            return;
        }
        profileSnapshotMapper.update(null, new LambdaUpdateWrapper<ForumUserAiProfileSnapshot>()
                .eq(ForumUserAiProfileSnapshot::getId, snapshot.getId())
                .set(ForumUserAiProfileSnapshot::getProfileVersion, (snapshot.getProfileVersion() == null ? 0L : snapshot.getProfileVersion()) + 1L)
                .set(ForumUserAiProfileSnapshot::getProfileJson, profileJson)
                .set(ForumUserAiProfileSnapshot::getFeatureVersion, result.getFeatureVersion() == null ? FEATURE_VERSION : result.getFeatureVersion())
                .set(ForumUserAiProfileSnapshot::getSourceWindowStart, start)
                .set(ForumUserAiProfileSnapshot::getSourceWindowEnd, now)
                .set(ForumUserAiProfileSnapshot::getRefreshAfter, refreshAfter)
                .set(ForumUserAiProfileSnapshot::getGeneratedBy, result.getGeneratedBy() == null ? "AI" : result.getGeneratedBy())
                .set(ForumUserAiProfileSnapshot::getDeleteState, DELETE_FALSE));
    }

    private SignalWindow collectSignals(Long userId) {
        Date since = new Date(System.currentTimeMillis() - FOURTEEN_DAYS_MILLIS);
        Map<Long, Double> recent7 = new HashMap<>();
        Map<Long, Double> recent14 = new HashMap<>();
        articleLikeMapper.selectList(new LambdaQueryWrapper<ArticleLike>()
                        .eq(ArticleLike::getUserId, userId)
                        .ge(ArticleLike::getCreateTime, since)
                        .select(ArticleLike::getArticleId, ArticleLike::getCreateTime))
                .forEach(item -> addSignal(recent7, recent14, item.getArticleId(), item.getCreateTime(), 2D));
        articleFavoriteMapper.selectList(new LambdaQueryWrapper<ArticleFavorite>()
                        .eq(ArticleFavorite::getUserId, userId)
                        .eq(ArticleFavorite::getDeleteState, DELETE_FALSE)
                        .ge(ArticleFavorite::getCreateTime, since)
                        .select(ArticleFavorite::getArticleId, ArticleFavorite::getCreateTime))
                .forEach(item -> addSignal(recent7, recent14, item.getArticleId(), item.getCreateTime(), 4D));
        articleReplyMapper.selectList(new LambdaQueryWrapper<ArticleReply>()
                        .eq(ArticleReply::getPostUserId, userId)
                        .eq(ArticleReply::getDeleteState, DELETE_FALSE)
                        .eq(ArticleReply::getState, STATE_ENABLED)
                        .ge(ArticleReply::getCreateTime, since)
                        .select(ArticleReply::getArticleId, ArticleReply::getCreateTime))
                .forEach(item -> addSignal(recent7, recent14, item.getArticleId(), item.getCreateTime(), 3D));
        List<UserRecommendFeedback> feedbacks = feedbackMapper.selectList(new LambdaQueryWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getUserId, userId)
                .eq(UserRecommendFeedback::getDeleteState, DELETE_FALSE)
                .ge(UserRecommendFeedback::getUpdateTime, since)
                .select(UserRecommendFeedback::getArticleId, UserRecommendFeedback::getReasonCode,
                        UserRecommendFeedback::getReasonDetail, UserRecommendFeedback::getUpdateTime));
        Map<Long, List<String>> negativeRecent7 = new HashMap<>();
        Map<Long, List<String>> negativeRecent14 = new HashMap<>();
        for (UserRecommendFeedback feedback : feedbacks) {
            addNegativeSignal(negativeRecent7, negativeRecent14, feedback);
        }
        return new SignalWindow(toBoardSignals(recent7), toBoardSignals(recent14),
                toNegativeBoardSignals(negativeRecent7), toNegativeBoardSignals(negativeRecent14));
    }

    private void addNegativeSignal(Map<Long, List<String>> recent7, Map<Long, List<String>> recent14,
            UserRecommendFeedback feedback) {
        if (feedback.getArticleId() == null || feedback.getUpdateTime() == null) {
            return;
        }
        long age = Math.max(0L, System.currentTimeMillis() - feedback.getUpdateTime().getTime());
        Map<Long, List<String>> target = age <= SEVEN_DAYS_MILLIS ? recent7
                : (age <= FOURTEEN_DAYS_MILLIS ? recent14 : null);
        if (target == null) {
            return;
        }
        String detail = feedback.getReasonDetail();
        String reason = feedback.getReasonCode() == null ? "UNRELATED" : feedback.getReasonCode();
        String signal = detail == null || detail.isBlank() ? reason : reason + ":" + detail.trim();
        target.computeIfAbsent(feedback.getArticleId(), key -> new ArrayList<>()).add(signal);
    }

    private List<Map<String, Object>> toNegativeBoardSignals(Map<Long, List<String>> feedbackByArticleId) {
        if (feedbackByArticleId.isEmpty()) {
            return List.of();
        }
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, feedbackByArticleId.keySet())
                .eq(Article::getDeleteState, DELETE_FALSE)
                .select(Article::getId, Article::getBoardId));
        Map<Long, String> boardNames = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                        .in(Board::getId, articles.stream().map(Article::getBoardId).distinct().toList())
                        .eq(Board::getDeleteState, DELETE_FALSE)
                        .eq(Board::getState, STATE_ENABLED)
                        .select(Board::getId, Board::getName))
                .stream().collect(java.util.stream.Collectors.toMap(Board::getId, Board::getName));
        Map<String, List<String>> reasonsByBoard = new HashMap<>();
        for (Article article : articles) {
            String boardName = boardNames.get(article.getBoardId());
            if (boardName != null) {
                reasonsByBoard.computeIfAbsent(boardName, key -> new ArrayList<>())
                        .addAll(feedbackByArticleId.getOrDefault(article.getId(), List.of()));
            }
        }
        return reasonsByBoard.entrySet().stream()
                .sorted(java.util.Comparator
                        .comparingInt((Map.Entry<String, List<String>> entry) -> entry.getValue().size())
                        .reversed())
                .limit(8)
                .map(entry -> Map.<String, Object>of("board", entry.getKey(), "score", entry.getValue().size(),
                        "reasons", entry.getValue().stream().limit(3).toList()))
                .toList();
    }

    private List<Map<String, Object>> toBoardSignals(Map<Long, Double> articleScores) {
        if (articleScores.isEmpty()) {
            return List.of();
        }
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, articleScores.keySet())
                .eq(Article::getDeleteState, DELETE_FALSE)
                .select(Article::getId, Article::getBoardId));
        Map<Long, Double> boardScores = new HashMap<>();
        for (Article article : articles) {
            boardScores.merge(article.getBoardId(), articleScores.getOrDefault(article.getId(), 0D), Double::sum);
        }
        if (boardScores.isEmpty()) {
            return List.of();
        }
        Map<Long, String> boardNames = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                        .in(Board::getId, boardScores.keySet())
                        .eq(Board::getDeleteState, DELETE_FALSE)
                        .eq(Board::getState, STATE_ENABLED)
                        .select(Board::getId, Board::getName))
                .stream().collect(java.util.stream.Collectors.toMap(Board::getId, Board::getName));
        List<Map<String, Object>> result = new ArrayList<>();
        boardScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(8)
                .forEach(entry -> {
                    String name = boardNames.get(entry.getKey());
                    if (name != null) {
                        result.add(Map.of("board", name, "score", entry.getValue()));
                    }
                });
        return result;
    }

    private ForumUserAiProfileSnapshot findProfileSnapshot(Long userId) {
        return profileSnapshotMapper.selectOne(new LambdaQueryWrapper<ForumUserAiProfileSnapshot>()
                .eq(ForumUserAiProfileSnapshot::getUserId, userId)
                .eq(ForumUserAiProfileSnapshot::getDeleteState, DELETE_FALSE));
    }

    private void deferProfileRefresh(Long userId) {
        profileSnapshotMapper.update(null, new LambdaUpdateWrapper<ForumUserAiProfileSnapshot>()
                .eq(ForumUserAiProfileSnapshot::getUserId, userId)
                .eq(ForumUserAiProfileSnapshot::getDeleteState, DELETE_FALSE)
                .set(ForumUserAiProfileSnapshot::getRefreshAfter,
                        new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000)));
    }

    private Map<String, Double> normalizedTopicWeights(Object rawTopics) {
        if (!(rawTopics instanceof List<?> topics)) {
            return Map.of();
        }
        Map<String, Double> result = new HashMap<>();
        for (Object raw : topics) {
            if (!(raw instanceof Map<?, ?> topic)) {
                continue;
            }
            String name = normalizeTopic(topic.get("name"));
            if (name == null) {
                continue;
            }
            Object rawWeight = topic.get("weight");
            double weight = rawWeight instanceof Number number ? number.doubleValue() : 0D;
            if (weight > 0D) {
                result.put(name, Math.min(weight, 1D));
            }
        }
        return result;
    }

    private void addSignal(Map<Long, Double> recent7, Map<Long, Double> recent14,
            Long articleId, Date createTime, double score) {
        if (articleId == null || createTime == null) {
            return;
        }
        long age = Math.max(0L, System.currentTimeMillis() - createTime.getTime());
        if (age <= SEVEN_DAYS_MILLIS) {
            recent7.merge(articleId, score, Double::sum);
        } else if (age <= FOURTEEN_DAYS_MILLIS) {
            recent14.merge(articleId, score * 0.35D, Double::sum);
        }
    }

    private String normalizeTopic(Object value) {
        if (value == null) {
            return null;
        }
        String topic = String.valueOf(value).trim().toLowerCase();
        return topic.isEmpty() ? null : topic;
    }

    private String sha256(String source) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte item : digest) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    private void executeAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record SignalWindow(List<Map<String, Object>> recent7, List<Map<String, Object>> recent14,
            List<Map<String, Object>> negativeRecent7, List<Map<String, Object>> negativeRecent14) {
    }
}
