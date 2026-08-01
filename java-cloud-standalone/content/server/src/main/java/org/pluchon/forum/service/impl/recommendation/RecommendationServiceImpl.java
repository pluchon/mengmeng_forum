package org.pluchon.forum.service.impl.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.RecommendationReasonType;
import org.pluchon.forum.common.enums.RecommendationFeedbackReason;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleFavorite;
import org.pluchon.forum.entity.db.ArticleLike;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.ForumArticleAiFeature;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.db.UserRecommendFeedback;
import org.pluchon.forum.entity.dto.recommendation.NotInterestedArticleRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.recommendation.RecommendArticleVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleFavoriteMapper;
import org.pluchon.forum.mapper.ArticleLikeMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.ForumArticleAiFeatureMapper;
import org.pluchon.forum.mapper.UserRecommendFeedbackMapper;
import org.pluchon.forum.service.impl.remote.UserInternalLookupService;
import org.pluchon.forum.service.interfaces.article.ArticleHotRankingService;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationService;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationAiProfileService;
import org.pluchon.forum.service.interfaces.recommendation.UserInterestPreferenceService;
import org.pluchon.forum.service.impl.remote.ContentFollowLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 为你推荐的可解释规则混排实现
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_ENABLED = 0;
    private static final int INTERACTION_HISTORY_LIMIT = 60;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private ArticleFavoriteMapper articleFavoriteMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private UserInternalLookupService userInternalLookupService;

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    private UserRecommendFeedbackMapper feedbackMapper;

    @Autowired
    private UserInterestPreferenceService preferenceService;

    @Autowired
    private ContentFollowLookupService userFollowService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    @Autowired
    private RecommendationAiProfileService recommendationAiProfileService;

    @Autowired
    private ForumArticleAiFeatureMapper articleFeatureMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResult<RecommendArticleVO> getFeed(Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        int expectedSize = Math.max(validPageSize * (validPageNum + 1), validPageSize);
        int candidateLimit = Math.max(expectedSize * 4, 80);
        Set<Long> feedbackArticleIds = loginUserId == null ? Set.of() : listFeedbackArticleIds(loginUserId);
        Set<Long> explicitBoardIds = loginUserId == null ? Set.of() : preferenceService.listActiveBoardIds(loginUserId);
        Map<Long, Double> interactionBoardScores = loginUserId == null
                ? Map.of()
                : listInteractionBoardScores(loginUserId);
        Set<Long> followingIds = loginUserId == null ? Set.of() : userFollowService.listFollowingIds(loginUserId);
        boolean personalized = loginUserId != null
                && preferenceService.isPersonalizationEnabled(loginUserId)
                && (!explicitBoardIds.isEmpty() || !interactionBoardScores.isEmpty() || !followingIds.isEmpty());

        Set<Long> relatedBoardIds = new LinkedHashSet<>(explicitBoardIds);
        relatedBoardIds.addAll(interactionBoardScores.keySet());
        Map<Long, Candidate> candidateMap = new LinkedHashMap<>();
        if (personalized) {
            addBoardCandidates(candidateMap,
                    listArticlesByBoards(relatedBoardIds, loginUserId, feedbackArticleIds, candidateLimit),
                    explicitBoardIds,
                    interactionBoardScores);
            addCandidates(candidateMap,
                    listArticlesByAuthors(followingIds, loginUserId, feedbackArticleIds, candidateLimit),
                    RecommendationReasonType.FOLLOWING,
                    30D);
        }
        addFreshCandidates(candidateMap,
                listFreshArticles(loginUserId, feedbackArticleIds, candidateLimit),
                personalized ? RecommendationReasonType.FRESH : RecommendationReasonType.COMMUNITY);
        addHotCandidates(candidateMap, listHotArticles(loginUserId, feedbackArticleIds, candidateLimit));
        if (personalized) {
            applyAiProfileScores(candidateMap, loginUserId);
        }

        List<Candidate> activeCandidates = retainActiveAuthors(new ArrayList<>(candidateMap.values()));
        List<Candidate> visibleCandidates = rankCandidates(activeCandidates, expectedSize, validPageSize);
        int fromIndex = Math.min((validPageNum - 1) * validPageSize, visibleCandidates.size());
        int toIndex = Math.min(fromIndex + validPageSize, visibleCandidates.size());
        List<RecommendArticleVO> records = buildResponse(visibleCandidates.subList(fromIndex, toIndex), followingIds);
        boolean hasNext = visibleCandidates.size() > toIndex;
        return new PageResult<>(records, (long) visibleCandidates.size(), validPageNum, validPageSize,
                (long) Math.max(1, (int) Math.ceil((double) visibleCandidates.size() / validPageSize)), hasNext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNotInterested(Long loginUserId, NotInterestedArticleRequest request) {
        requireUserId(loginUserId);
        if (request == null || request.getArticleId() == null || request.getArticleId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String reasonCode = request.getReasonCode() == null ? "" : request.getReasonCode().trim();
        if (!RecommendationFeedbackReason.isSupported(reasonCode)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String reasonDetail = request.getReasonDetail() == null ? null : request.getReasonDetail().trim();
        if (reasonDetail != null && reasonDetail.isEmpty()) {
            reasonDetail = null;
        }
        Article article = articleMapper.selectOne(visibleArticleWrapper(loginUserId, Set.of())
                .eq(Article::getId, request.getArticleId()));
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        UserRecommendFeedback existing = feedbackMapper.selectOne(new LambdaQueryWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getUserId, loginUserId)
                .eq(UserRecommendFeedback::getArticleId, request.getArticleId()));
        if (existing == null) {
            UserRecommendFeedback record = new UserRecommendFeedback();
            record.setUserId(loginUserId);
            record.setArticleId(request.getArticleId());
            record.setReasonCode(reasonCode);
            record.setReasonDetail(reasonDetail);
            record.setDeleteState(DELETE_FALSE);
            feedbackMapper.insert(record);
            recommendationAiProfileService.requestProfileRefresh(loginUserId);
            return;
        }
        feedbackMapper.update(null, new LambdaUpdateWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getId, existing.getId())
                .set(UserRecommendFeedback::getReasonCode, reasonCode)
                .set(UserRecommendFeedback::getReasonDetail, reasonDetail)
                .set(UserRecommendFeedback::getDeleteState, DELETE_FALSE));
        recommendationAiProfileService.requestProfileRefresh(loginUserId);
    }

    @Override
    public PageResult<RecommendArticleVO> getNotInterestedArticles(Long loginUserId, Integer pageNum, Integer pageSize) {
        requireUserId(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<UserRecommendFeedback> feedbackPage = feedbackMapper.selectPage(new Page<>(validPageNum, validPageSize),
                new LambdaQueryWrapper<UserRecommendFeedback>()
                        .eq(UserRecommendFeedback::getUserId, loginUserId)
                        .eq(UserRecommendFeedback::getDeleteState, DELETE_FALSE)
                        .orderByDesc(UserRecommendFeedback::getUpdateTime)
                        .orderByDesc(UserRecommendFeedback::getId));
        List<UserRecommendFeedback> feedbackRecords = feedbackPage.getRecords();
        if (feedbackRecords.isEmpty()) {
            return new PageResult<>(List.of(), feedbackPage.getTotal(), validPageNum, validPageSize,
                    feedbackPage.getPages(), feedbackPage.hasNext());
        }
        List<Long> articleIds = feedbackRecords.stream().map(UserRecommendFeedback::getArticleId).toList();
        Map<Long, Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, articleIds)
                        .eq(Article::getDeleteState, DELETE_FALSE)
                        .eq(Article::getState, STATE_ENABLED)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Article::getId, item -> item));
        Set<Long> authorIds = articles.values().stream().map(Article::getUserId).collect(java.util.stream.Collectors.toSet());
        Map<Long, User> users = authorIds.isEmpty() ? Map.of() : userInternalLookupService.loadActiveUsers(authorIds);
        List<RecommendArticleVO> records = new ArrayList<>();
        for (UserRecommendFeedback feedback : feedbackRecords) {
            Article article = articles.get(feedback.getArticleId());
            User author = article == null ? null : users.get(article.getUserId());
            if (article == null || author == null) {
                continue;
            }
            RecommendArticleVO response = new RecommendArticleVO();
            response.setArticle(article);
            response.setUser(new UserBriefVO(author));
            records.add(response);
        }
        return new PageResult<>(records, feedbackPage.getTotal(), validPageNum, validPageSize,
                feedbackPage.getPages(), feedbackPage.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreInterested(Long loginUserId, Long articleId) {
        requireUserId(loginUserId);
        if (articleId == null || articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        feedbackMapper.update(null, new LambdaUpdateWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getUserId, loginUserId)
                .eq(UserRecommendFeedback::getArticleId, articleId)
                .eq(UserRecommendFeedback::getDeleteState, DELETE_FALSE)
                .set(UserRecommendFeedback::getDeleteState, DELETE_TRUE));
        recommendationAiProfileService.requestProfileRefresh(loginUserId);
    }

    private List<Candidate> rankCandidates(List<Candidate> candidates, int expectedSize, int pageSize) {
        List<Candidate> ordered = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::getScore).reversed()
                        .thenComparing(item -> item.getArticle().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.getArticle().getId(), Comparator.reverseOrder()))
                .toList();
        List<Candidate> result = new ArrayList<>();
        addCandidates(result, ordered, expectedSize, pageSize, false);
        addCandidates(result, ordered, expectedSize, pageSize, true);
        return result;
    }

    private void addCandidates(List<Candidate> result, List<Candidate> candidates, int targetSize, int pageSize,
            boolean allowAdjacentBoard) {
        for (Candidate candidate : candidates) {
            if (result.size() >= targetSize) {
                return;
            }
            if (containsArticle(result, candidate.getArticle().getId())) {
                continue;
            }
            int currentPage = result.size() / pageSize;
            if (countAuthorInPage(result, candidate.getArticle().getUserId(), currentPage, pageSize) >= 2) {
                continue;
            }
            if (!allowAdjacentBoard && !result.isEmpty()
                    && Objects.equals(result.get(result.size() - 1).getArticle().getBoardId(), candidate.getArticle().getBoardId())) {
                continue;
            }
            result.add(candidate);
        }
    }

    private boolean containsArticle(List<Candidate> candidates, Long articleId) {
        return candidates.stream().anyMatch(item -> Objects.equals(item.getArticle().getId(), articleId));
    }

    private long countAuthorInPage(List<Candidate> candidates, Long authorId, int page, int pageSize) {
        int fromIndex = page * pageSize;
        return candidates.subList(Math.min(fromIndex, candidates.size()), candidates.size()).stream()
                .filter(item -> Objects.equals(item.getArticle().getUserId(), authorId))
                .count();
    }

    private List<Candidate> retainActiveAuthors(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = candidates.stream().map(item -> item.getArticle().getUserId()).collect(java.util.stream.Collectors.toSet());
        Set<Long> activeAuthorIds = userInternalLookupService.filterActiveUserIds(authorIds);
        return candidates.stream().filter(item -> activeAuthorIds.contains(item.getArticle().getUserId())).toList();
    }

    private List<RecommendArticleVO> buildResponse(List<Candidate> candidates, Set<Long> followingIds) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = candidates.stream().map(item -> item.getArticle().getUserId()).collect(java.util.stream.Collectors.toSet());
        Map<Long, User> users = userInternalLookupService.loadActiveUsers(authorIds);
        Map<Long, String> boardNames = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                        .in(Board::getId, candidates.stream().map(item -> item.getArticle().getBoardId()).distinct().toList())
                        .eq(Board::getDeleteState, DELETE_FALSE)
                        .eq(Board::getState, STATE_ENABLED))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Board::getId, Board::getName));
        List<RecommendArticleVO> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            User author = users.get(candidate.getArticle().getUserId());
            if (author == null) {
                continue;
            }
            RecommendArticleVO response = new RecommendArticleVO();
            response.setArticle(candidate.getArticle());
            response.setUser(new UserBriefVO(author));
            result.add(response);
        }
        return result;
    }

    private void addBoardCandidates(Map<Long, Candidate> candidateMap, List<Article> articles,
            Set<Long> explicitBoardIds, Map<Long, Double> interactionBoardScores) {
        double maximumInteractionScore = interactionBoardScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D);
        for (Article article : articles) {
            boolean explicitlySelected = explicitBoardIds.contains(article.getBoardId());
            RecommendationReasonType reason = explicitlySelected
                    ? RecommendationReasonType.INTEREST
                    : RecommendationReasonType.INTERACTION;
            double interactionScore = interactionBoardScores.getOrDefault(article.getBoardId(), 0D);
            double normalizedInteractionScore = maximumInteractionScore <= 0D ? 0D : interactionScore / maximumInteractionScore;
            double score = 18D + normalizedInteractionScore * 24D + (explicitlySelected ? 32D : 0D);
            mergeCandidate(candidateMap, article, reason, score);
        }
    }

    private void addCandidates(Map<Long, Candidate> candidateMap, List<Article> articles,
            RecommendationReasonType reason, double score) {
        for (Article article : articles) {
            mergeCandidate(candidateMap, article, reason, score);
        }
    }

    private void addFreshCandidates(Map<Long, Candidate> candidateMap, List<Article> articles,
            RecommendationReasonType reason) {
        for (Article article : articles) {
            mergeCandidate(candidateMap, article, reason, 8D + freshnessScore(article.getCreateTime()));
        }
    }

    private void addHotCandidates(Map<Long, Candidate> candidateMap, List<Article> articles) {
        for (int index = 0; index < articles.size(); index++) {
            mergeCandidate(candidateMap, articles.get(index), RecommendationReasonType.HOT,
                    Math.max(12D, 26D - index * 0.35D));
        }
    }

    private void mergeCandidate(Map<Long, Candidate> candidateMap, Article article,
            RecommendationReasonType reason, double score) {
        Candidate existing = candidateMap.get(article.getId());
        if (existing == null) {
            candidateMap.put(article.getId(), new Candidate(article, reason, score));
            return;
        }
        existing.addScore(score);
        if (reasonPriority(reason) > reasonPriority(existing.getReason())) {
            existing.setReason(reason);
        }
    }

    private int reasonPriority(RecommendationReasonType reason) {
        return switch (reason) {
            case INTEREST -> 6;
            case FOLLOWING -> 5;
            case AI_PROFILE -> 4;
            case INTERACTION -> 3;
            case HOT -> 2;
            case FRESH -> 1;
            case COMMUNITY -> 0;
        };
    }

    private void applyAiProfileScores(Map<Long, Candidate> candidateMap, Long userId) {
        if (candidateMap.isEmpty()) {
            return;
        }
        Map<String, Double> userTopics = recommendationAiProfileService.getActiveTopicWeights(userId);
        Map<String, Double> avoidTopics = recommendationAiProfileService.getAvoidTopicWeights(userId);
        if (userTopics.isEmpty() && avoidTopics.isEmpty()) {
            return;
        }
        List<ForumArticleAiFeature> features = articleFeatureMapper.selectList(new LambdaQueryWrapper<ForumArticleAiFeature>()
                .in(ForumArticleAiFeature::getArticleId, candidateMap.keySet())
                .eq(ForumArticleAiFeature::getDeleteState, DELETE_FALSE)
                .select(ForumArticleAiFeature::getArticleId, ForumArticleAiFeature::getFeatureJson));
        for (ForumArticleAiFeature feature : features) {
            Candidate candidate = candidateMap.get(feature.getArticleId());
            if (candidate == null) {
                continue;
            }
            double overlap = calculateTopicOverlap(userTopics, feature.getFeatureJson());
            if (overlap > 0D) {
                mergeCandidate(candidateMap, candidate.getArticle(), RecommendationReasonType.AI_PROFILE, overlap * 18D);
            }
            double avoidOverlap = calculateTopicOverlap(avoidTopics, feature.getFeatureJson());
            if (avoidOverlap > 0D) {
                candidate.addScore(-avoidOverlap * 20D);
            }
        }
    }

    private double calculateTopicOverlap(Map<String, Double> userTopics, String featureJson) {
        if (featureJson == null || featureJson.isBlank()) {
            return 0D;
        }
        try {
            Map<String, Object> feature = objectMapper.readValue(featureJson, new TypeReference<>() { });
            Object rawTopics = feature.get("topics");
            if (!(rawTopics instanceof List<?> topics)) {
                return 0D;
            }
            double overlap = 0D;
            for (Object rawTopic : topics) {
                if (!(rawTopic instanceof Map<?, ?> topic)) {
                    continue;
                }
                Object rawName = topic.get("name");
                Object rawWeight = topic.get("weight");
                if (rawName == null || !(rawWeight instanceof Number weight)) {
                    continue;
                }
                String name = String.valueOf(rawName).trim().toLowerCase();
                overlap += userTopics.getOrDefault(name, 0D) * Math.min(weight.doubleValue(), 1D);
            }
            return Math.min(overlap, 1D);
        } catch (Exception e) {
            return 0D;
        }
    }

    private double freshnessScore(Date createTime) {
        if (createTime == null) {
            return 0D;
        }
        long ageHours = Math.max(0L, (System.currentTimeMillis() - createTime.getTime()) / 3_600_000L);
        return Math.max(0D, 12D - ageHours / 12D);
    }

    private List<Article> listArticlesByBoards(Set<Long> boardIds, Long userId, Set<Long> feedbackArticleIds, int limit) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectPage(PageUtils.getPage(1, limit), visibleArticleWrapper(userId, feedbackArticleIds)
                .in(Article::getBoardId, boardIds)
                .orderByDesc(Article::getUpdateTime)
                .orderByDesc(Article::getId)).getRecords();
    }

    private List<Article> listArticlesByAuthors(Set<Long> authorIds, Long userId, Set<Long> feedbackArticleIds, int limit) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectPage(PageUtils.getPage(1, limit), visibleArticleWrapper(userId, feedbackArticleIds)
                .in(Article::getUserId, authorIds)
                .orderByDesc(Article::getUpdateTime)
                .orderByDesc(Article::getId)).getRecords();
    }

    private List<Article> listFreshArticles(Long userId, Set<Long> feedbackArticleIds, int limit) {
        return articleMapper.selectPage(PageUtils.getPage(1, limit), visibleArticleWrapper(userId, feedbackArticleIds)
                .orderByDesc(Article::getCreateTime)
                .orderByDesc(Article::getId)).getRecords();
    }

    private List<Article> listHotArticles(Long userId, Set<Long> feedbackArticleIds, int limit) {
        List<Long> hotIds = articleHotRankingService.getHotArticleList(limit);
        if (hotIds == null || hotIds.isEmpty()) {
            return List.of();
        }
        List<Article> articles = articleMapper.selectList(visibleArticleWrapper(userId, feedbackArticleIds)
                .in(Article::getId, hotIds));
        Map<Long, Article> articleMap = articles.stream().collect(java.util.stream.Collectors.toMap(Article::getId, item -> item));
        List<Article> result = new ArrayList<>();
        for (Long hotId : hotIds) {
            Article article = articleMap.get(hotId);
            if (article != null) {
                result.add(article);
            }
        }
        return result;
    }

    private LambdaQueryWrapper<Article> visibleArticleWrapper(Long userId, Set<Long> feedbackArticleIds) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getDeleteState, DELETE_FALSE)
                .eq(Article::getState, STATE_ENABLED)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode());
        if (userId != null && userId > 0) {
            wrapper.ne(Article::getUserId, userId);
        }
        if (feedbackArticleIds != null && !feedbackArticleIds.isEmpty()) {
            wrapper.notIn(Article::getId, feedbackArticleIds);
        }
        return wrapper;
    }

    private Map<Long, Double> listInteractionBoardScores(Long userId) {
        Map<Long, Double> articleScores = new HashMap<>();
        addLikeInteractionScores(articleScores, articleLikeMapper.selectPage(new Page<>(1, INTERACTION_HISTORY_LIMIT),
                new LambdaQueryWrapper<ArticleLike>()
                        .eq(ArticleLike::getUserId, userId)
                        .orderByDesc(ArticleLike::getCreateTime)
                        .select(ArticleLike::getArticleId, ArticleLike::getCreateTime)).getRecords(), 2D);
        addFavoriteInteractionScores(articleScores, articleFavoriteMapper.selectPage(new Page<>(1, INTERACTION_HISTORY_LIMIT),
                new LambdaQueryWrapper<ArticleFavorite>()
                        .eq(ArticleFavorite::getUserId, userId)
                        .eq(ArticleFavorite::getDeleteState, DELETE_FALSE)
                        .orderByDesc(ArticleFavorite::getCreateTime)
                        .select(ArticleFavorite::getArticleId, ArticleFavorite::getCreateTime)).getRecords(), 4D);
        addReplyInteractionScores(articleScores, articleReplyMapper.selectPage(new Page<>(1, INTERACTION_HISTORY_LIMIT),
                new LambdaQueryWrapper<ArticleReply>()
                        .eq(ArticleReply::getPostUserId, userId)
                        .eq(ArticleReply::getDeleteState, DELETE_FALSE)
                        .eq(ArticleReply::getState, STATE_ENABLED)
                        .orderByDesc(ArticleReply::getCreateTime)
                        .select(ArticleReply::getArticleId, ArticleReply::getCreateTime)).getRecords(), 3D);
        if (articleScores.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> boardScores = new HashMap<>();
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, articleScores.keySet())
                .eq(Article::getDeleteState, DELETE_FALSE)
                .eq(Article::getState, STATE_ENABLED)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .select(Article::getId, Article::getBoardId));
        for (Article article : articles) {
            boardScores.merge(article.getBoardId(), articleScores.get(article.getId()), Double::sum);
        }
        return boardScores;
    }

    private void addLikeInteractionScores(Map<Long, Double> articleScores, List<ArticleLike> records, double baseScore) {
        for (ArticleLike record : records) {
            addInteractionScore(articleScores, record.getArticleId(), record.getCreateTime(), baseScore);
        }
    }

    private void addFavoriteInteractionScores(Map<Long, Double> articleScores, List<ArticleFavorite> records, double baseScore) {
        for (ArticleFavorite record : records) {
            addInteractionScore(articleScores, record.getArticleId(), record.getCreateTime(), baseScore);
        }
    }

    private void addReplyInteractionScores(Map<Long, Double> articleScores, List<ArticleReply> records, double baseScore) {
        for (ArticleReply record : records) {
            addInteractionScore(articleScores, record.getArticleId(), record.getCreateTime(), baseScore);
        }
    }

    private void addInteractionScore(Map<Long, Double> articleScores, Long articleId, Date createTime, double baseScore) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        articleScores.merge(articleId, baseScore * interactionRecencyWeight(createTime), Double::sum);
    }

    private double interactionRecencyWeight(Date createTime) {
        if (createTime == null) {
            return 0.25D;
        }
        long ageDays = Math.max(0L, (System.currentTimeMillis() - createTime.getTime()) / 86_400_000L);
        if (ageDays <= 30L) {
            return 1D;
        }
        if (ageDays <= 90L) {
            return 0.6D;
        }
        return 0.25D;
    }

    private Set<Long> listFeedbackArticleIds(Long userId) {
        return feedbackMapper.selectList(new LambdaQueryWrapper<UserRecommendFeedback>()
                        .eq(UserRecommendFeedback::getUserId, userId)
                        .eq(UserRecommendFeedback::getDeleteState, DELETE_FALSE)
                        .select(UserRecommendFeedback::getArticleId))
                .stream()
                .map(UserRecommendFeedback::getArticleId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
    }

    // 推荐候选及其累积排序分
    private static class Candidate {

        // 帖子实体
        private final Article article;

        // 前端展示的主要推荐理由
        private RecommendationReasonType reason;

        // 多种召回信号累计后的排序分
        private double score;

        Candidate(Article article, RecommendationReasonType reason, double score) {
            this.article = article;
            this.reason = reason;
            this.score = score;
        }

        Article getArticle() {
            return article;
        }

        RecommendationReasonType getReason() {
            return reason;
        }

        void setReason(RecommendationReasonType reason) {
            this.reason = reason;
        }

        double getScore() {
            return score;
        }

        void addScore(double score) {
            this.score += score;
        }
    }
}
