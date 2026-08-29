package org.pluchon.forum.service.impl.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.ai.AiRagSearchRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ArticleStatus;
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
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.db.UserRecommendFeedback;
import org.pluchon.forum.entity.dto.recommendation.NotInterestedArticleRequest;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.recommendation.RecommendArticleVO;
import org.pluchon.forum.mapper.ArticleFavoriteMapper;
import org.pluchon.forum.mapper.ArticleLikeMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.ForumArticleAiFeatureMapper;
import org.pluchon.forum.mapper.UserRecommendFeedbackMapper;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.pluchon.forum.service.interfaces.article.ArticleHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleMediaService;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationService;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationAiProfileService;
import org.pluchon.forum.service.interfaces.recommendation.UserRecommendationSettingService;
import org.pluchon.forum.service.impl.remote.ContentFollowLookupService;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.pluchon.forum.config.ForumSearchProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 为你推荐的可解释规则混排实现
@Slf4j
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_ENABLED = 0;
    private static final int INTERACTION_HISTORY_LIMIT = 60;

    // 向量候选最低相似度由配置提供，见 forum.search.recommend-vector-min-score
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ForumSearchProperties forumSearchProperties;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private ArticleFavoriteMapper articleFavoriteMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ContentUserLookupService userInternalLookupService;

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    private UserRecommendFeedbackMapper feedbackMapper;

    @Autowired
    private ContentFollowLookupService userFollowService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    @Autowired
    private ArticleMediaService articleMediaService;

    @Autowired
    private RecommendationAiProfileService recommendationAiProfileService;

    @Autowired
    private UserRecommendationSettingService userRecommendationSettingService;

    @Autowired
    private ForumArticleAiFeatureMapper articleFeatureMapper;

    @Autowired
    private ContentAiGatewayService contentAiGatewayService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResult<RecommendArticleVO> getFeed(Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Set<Long> followingIds = loginUserId == null ? Set.of() : userFollowService.listFollowingIds(loginUserId);
        // 原本每翻一页都把五路召回重跑一遍再切片。fresh / hot 依赖实时数据，
        // 两次请求算出的顺序并不一致，用户会重复看到同一篇、或者永远错过某一篇；
        // 而且 expectedSize 随页码增长，越往后翻捞得越多，向量召回还要再打一次 Python。
        // 排序结果本身是确定的，缓存一份，翻页只切片
        List<Long> rankedIds = readRankCache(loginUserId);
        if (rankedIds.isEmpty()) {
            rankedIds = computeRankedIds(loginUserId, followingIds);
            writeRankCache(loginUserId, rankedIds);
        }
        return buildFeedPage(rankedIds, loginUserId, followingIds, validPageNum, validPageSize);
    }

    private List<Long> computeRankedIds(Long loginUserId, Set<Long> followingIds) {
        int expectedSize = Constant.RECOMMEND_FEED_CACHE_SIZE;
        int candidateLimit = Math.max(expectedSize * 4, 80);
        Set<Long> feedbackArticleIds = loginUserId == null ? Set.of() : listFeedbackArticleIds(loginUserId);
        Set<Long> explicitBoardIds = loginUserId == null
                ? Set.of()
                : userRecommendationSettingService.getInterestBoardIds(loginUserId);
        Map<Long, Double> interactionBoardScores = loginUserId == null
                ? Map.of()
                : listInteractionBoardScores(loginUserId);
        boolean personalized = loginUserId != null
                && userRecommendationSettingService.isPersonalizedEnabled(loginUserId)
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
                    30D);
            addVectorCandidates(candidateMap, loginUserId, feedbackArticleIds, candidateLimit);
        }
        addFreshCandidates(candidateMap,
                listFreshArticles(loginUserId, feedbackArticleIds, candidateLimit));
        addHotCandidates(candidateMap, listHotArticles(loginUserId, feedbackArticleIds, candidateLimit));
        if (personalized) {
            applyAiProfileScores(candidateMap, loginUserId);
        }

        List<Candidate> activeCandidates = retainActiveAuthors(new ArrayList<>(candidateMap.values()));
        List<Candidate> visibleCandidates = rankCandidates(activeCandidates, expectedSize);
        return visibleCandidates.stream().map(item -> item.getArticle().getId()).toList();
    }

    // 缓存里只有顺序，可见性每次现查：这几分钟内被删除或下架的帖子不能因为命中缓存漏出去
    private PageResult<RecommendArticleVO> buildFeedPage(List<Long> rankedIds, Long loginUserId,
            Set<Long> followingIds, int pageNum, int pageSize) {
        Set<Long> feedbackArticleIds = loginUserId == null ? Set.of() : listFeedbackArticleIds(loginUserId);
        List<Long> visibleIds = retainVisibleArticleIds(rankedIds, loginUserId, feedbackArticleIds);
        long total = visibleIds.size();
        int fromIndex = Math.min((pageNum - 1) * pageSize, visibleIds.size());
        int toIndex = Math.min(fromIndex + pageSize, visibleIds.size());
        List<RecommendArticleVO> records = buildResponseByIds(visibleIds.subList(fromIndex, toIndex), followingIds);
        long pages = total == 0 ? 1 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(records, total, pageNum, pageSize, pages, visibleIds.size() > toIndex);
    }

    private List<Long> retainVisibleArticleIds(List<Long> orderedIds, Long loginUserId, Set<Long> feedbackArticleIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Set<Long> alive = articleMapper.selectList(visibleArticleWrapper(loginUserId, feedbackArticleIds)
                        .in(Article::getId, orderedIds)
                        .select(Article::getId))
                .stream().map(Article::getId).collect(Collectors.toSet());
        return orderedIds.stream().filter(alive::contains).toList();
    }

    private String rankCacheKey(Long loginUserId) {
        return Constant.REDIS_KEY_RECOMMEND_FEED_RANK + (loginUserId == null ? "guest" : loginUserId);
    }

    private List<Long> readRankCache(Long loginUserId) {
        String cached = stringRedisTemplate.opsForValue().get(rankCacheKey(loginUserId));
        if (cached == null || cached.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : cached.split(",")) {
            try {
                ids.add(Long.valueOf(part));
            } catch (NumberFormatException ignored) {
                // 脏数据当作没命中
            }
        }
        return ids;
    }

    private void writeRankCache(Long loginUserId, List<Long> rankedIds) {
        if (rankedIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(rankCacheKey(loginUserId),
                rankedIds.stream().map(String::valueOf).collect(Collectors.joining(",")),
                Constant.REDIS_TTL_RECOMMEND_FEED_RANK, java.util.concurrent.TimeUnit.SECONDS);
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
                        .orderByDesc(UserRecommendFeedback::getCreateTime)
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
        Map<Long, UserInternalVO> users = authorIds.isEmpty() ? Map.of() : userInternalLookupService.loadActiveUsers(authorIds);
        Map<Long, Integer> imageCounts = articleMediaService.countImagesByArticleIds(articleIds);
        Map<Long, String> firstImageUrls = articleMediaService.firstImageUrlByArticleIds(articleIds);
        List<RecommendArticleVO> records = new ArrayList<>();
        for (UserRecommendFeedback feedback : feedbackRecords) {
            Article article = articles.get(feedback.getArticleId());
            UserInternalVO author = article == null ? null : users.get(article.getUserId());
            if (article == null || author == null) {
                continue;
            }
            RecommendArticleVO response = new RecommendArticleVO();
            response.setArticle(article);
            response.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(author));
            response.setImageCount(imageCounts.getOrDefault(article.getId(), 0));
            response.setFirstImageUrl(firstImageUrls.get(article.getId()));
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

    private List<Candidate> rankCandidates(List<Candidate> candidates, int expectedSize) {
        List<Candidate> ordered = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::getScore).reversed()
                        .thenComparing(item -> item.getArticle().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.getArticle().getId(), Comparator.reverseOrder()))
                .toList();
        long distinctBoards = ordered.stream()
                .map(item -> item.getArticle().getBoardId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        List<Candidate> result = new ArrayList<>();
        Set<Long> pickedArticleIds = new HashSet<>();
        Map<Long, Integer> authorCount = new HashMap<>();
        // 候选板块本来就没几种时不做相邻打散：只选了一个兴趣板块的用户，
        // 高分内容会被这条规则整体推到列表末尾，反而看不到自己选的东西
        if (distinctBoards >= Constant.RECOMMEND_DIVERSITY_MIN_BOARDS) {
            addCandidates(result, ordered, expectedSize, pickedArticleIds, authorCount, false);
        }
        addCandidates(result, ordered, expectedSize, pickedArticleIds, authorCount, true);
        return result;
    }

    // 同作者上限按整份榜单算，不再按页：翻页已经改成切同一份缓存，页边界不再有意义
    private static final int MAX_ARTICLES_PER_AUTHOR = 3;

    private void addCandidates(List<Candidate> result, List<Candidate> candidates, int targetSize,
            Set<Long> pickedArticleIds, Map<Long, Integer> authorCount, boolean allowAdjacentBoard) {
        for (Candidate candidate : candidates) {
            if (result.size() >= targetSize) {
                return;
            }
            Long articleId = candidate.getArticle().getId();
            if (!pickedArticleIds.add(articleId)) {
                continue;
            }
            Long authorId = candidate.getArticle().getUserId();
            if (authorCount.getOrDefault(authorId, 0) >= MAX_ARTICLES_PER_AUTHOR) {
                pickedArticleIds.remove(articleId);
                continue;
            }
            if (!allowAdjacentBoard && !result.isEmpty()
                    && Objects.equals(result.get(result.size() - 1).getArticle().getBoardId(),
                            candidate.getArticle().getBoardId())) {
                pickedArticleIds.remove(articleId);
                continue;
            }
            authorCount.merge(authorId, 1, Integer::sum);
            result.add(candidate);
        }
    }

    private List<Candidate> retainActiveAuthors(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = candidates.stream().map(item -> item.getArticle().getUserId()).collect(java.util.stream.Collectors.toSet());
        Set<Long> activeAuthorIds = userInternalLookupService.filterActiveUserIds(authorIds);
        return candidates.stream().filter(item -> activeAuthorIds.contains(item.getArticle().getUserId())).toList();
    }

    private List<RecommendArticleVO> buildResponseByIds(List<Long> orderedIds, Set<Long> followingIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Article> articleMap = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, orderedIds))
                .stream().collect(Collectors.toMap(Article::getId, item -> item));
        List<Article> candidates = orderedIds.stream().map(articleMap::get).filter(Objects::nonNull).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = candidates.stream().map(Article::getUserId).collect(Collectors.toSet());
        Map<Long, UserInternalVO> users = userInternalLookupService.loadActiveUsers(authorIds);
        Map<Long, String> boardNames = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                        .in(Board::getId, candidates.stream().map(Article::getBoardId).distinct().toList())
                        .eq(Board::getDeleteState, DELETE_FALSE)
                        .eq(Board::getState, STATE_ENABLED))
                .stream()
                .collect(Collectors.toMap(Board::getId, Board::getName));
        Map<Long, Integer> imageCounts = articleMediaService.countImagesByArticleIds(
                candidates.stream().map(Article::getId).toList());
        Map<Long, String> firstImageUrls = articleMediaService.firstImageUrlByArticleIds(
                candidates.stream().map(Article::getId).toList());
        List<RecommendArticleVO> result = new ArrayList<>();
        for (Article article : candidates) {
            UserInternalVO author = users.get(article.getUserId());
            if (author == null) {
                continue;
            }
            RecommendArticleVO response = new RecommendArticleVO();
            response.setArticle(article);
            response.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(author));
            response.setImageCount(imageCounts.getOrDefault(article.getId(), 0));
            response.setFirstImageUrl(firstImageUrls.get(article.getId()));
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
            double interactionScore = interactionBoardScores.getOrDefault(article.getBoardId(), 0D);
            double normalizedInteractionScore = maximumInteractionScore <= 0D ? 0D : interactionScore / maximumInteractionScore;
            double score = 18D + normalizedInteractionScore * 24D + (explicitlySelected ? 32D : 0D);
            mergeCandidate(candidateMap, article, score);
        }
    }

    private void addCandidates(Map<Long, Candidate> candidateMap, List<Article> articles,
            double score) {
        for (Article article : articles) {
            mergeCandidate(candidateMap, article, score);
        }
    }

    private void addFreshCandidates(Map<Long, Candidate> candidateMap, List<Article> articles) {
        for (Article article : articles) {
            mergeCandidate(candidateMap, article, 8D + freshnessScore(article.getCreateTime()));
        }
    }

    private void addHotCandidates(Map<Long, Candidate> candidateMap, List<Article> articles) {
        for (int index = 0; index < articles.size(); index++) {
            mergeCandidate(candidateMap, articles.get(index),
                    Math.max(12D, 26D - index * 0.35D));
        }
    }

    // 推荐理由只影响卡片上的展示文案，不参与排序（分数是无条件累加的），已按需求去掉
    private void mergeCandidate(Map<Long, Candidate> candidateMap, Article article, double score) {
        Candidate existing = candidateMap.get(article.getId());
        if (existing == null) {
            candidateMap.put(article.getId(), new Candidate(article, score));
            return;
        }
        existing.addScore(score);
    }

    private void addVectorCandidates(Map<Long, Candidate> candidateMap, Long userId,
            Set<Long> feedbackArticleIds, int limit) {
        String preferenceQuery = resolvePreferenceQuery(userId);
        if (preferenceQuery.isBlank()) {
            return;
        }
        List<RagArticleVectorHitVO> hits;
        try {
            AiRagSearchRequest request = new AiRagSearchRequest();
            request.setQuery(preferenceQuery);
            request.setCandidates(List.of());
            hits = contentAiGatewayService.ragArticleVectorRanked(request);
        } catch (Exception e) {
            log.warn("推荐向量召回失败 userId={}: {}", userId, e.getMessage());
            return;
        }
        if (hits == null || hits.isEmpty()) {
            return;
        }
        Map<Long, Double> similarityById = new LinkedHashMap<>();
        for (RagArticleVectorHitVO hit : hits) {
            if (hit == null || hit.getArticleId() == null) {
                continue;
            }
            double similarity = hit.getScore() == null ? 0D : hit.getScore();
            if (similarity < forumSearchProperties.getRecommendVectorMinScore()) {
                continue;
            }
            similarityById.putIfAbsent(hit.getArticleId(), Math.min(similarity, 1D));
            if (similarityById.size() >= Constant.SEARCH_RAG_CANDIDATE_LIMIT) {
                break;
            }
        }
        if (similarityById.isEmpty()) {
            return;
        }
        List<Article> articles = articleMapper.selectList(visibleArticleWrapper(userId, feedbackArticleIds)
                .in(Article::getId, similarityById.keySet()));
        Map<Long, Article> articleMap = articles.stream().collect(Collectors.toMap(Article::getId, item -> item));
        int added = 0;
        for (Map.Entry<Long, Double> entry : similarityById.entrySet()) {
            if (added >= limit) {
                break;
            }
            Article article = articleMap.get(entry.getKey());
            if (article == null) {
                continue;
            }
            mergeCandidate(candidateMap, article, 10D + entry.getValue() * 28D);
            added++;
        }
    }

    private String resolvePreferenceQuery(Long userId) {
        String fromProfile = recommendationAiProfileService.getPreferenceQuery(userId);
        if (fromProfile != null && !fromProfile.isBlank()) {
            return fromProfile.trim();
        }
        List<String> boardNames = userRecommendationSettingService.getInterestBoardNames(userId);
        if (boardNames.isEmpty()) {
            return "";
        }
        String joined = String.join(" ", boardNames);
        return joined.length() > 200 ? joined.substring(0, 200) : joined;
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
                mergeCandidate(candidateMap, candidate.getArticle(), overlap * 18D);
            }
            double avoidOverlap = calculateTopicOverlap(avoidTopics, feature.getFeatureJson());
            if (avoidOverlap > 0D) {
                candidate.addScore(-avoidOverlap * 35D);
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

        // 多种召回信号累计后的排序分
        private double score;

        Candidate(Article article, double score) {
            this.article = article;
            this.score = score;
        }

        Article getArticle() {
            return article;
        }

        double getScore() {
            return score;
        }

        void addScore(double score) {
            this.score += score;
        }
    }
}
