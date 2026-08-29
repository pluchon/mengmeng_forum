package org.pluchon.forum.service.impl.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.constant.ForumBusinessConstants;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.RedisWindowCounter;
import org.pluchon.forum.converter.SearchUserConverter;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ForumArticleAiFeature;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.search.SearchArticleResponse;
import org.pluchon.forum.entity.vo.search.SearchUserItemVO;
import org.pluchon.forum.entity.vo.search.SearchUserResponse;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ForumArticleAiFeatureMapper;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;
import org.pluchon.forum.api.ai.AiRagSearchRequest;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.pluchon.forum.service.interfaces.search.ArticleSearchIndexService;
import org.pluchon.forum.service.interfaces.search.SearchService;
import org.pluchon.forum.service.impl.remote.ContentFollowLookupService;
import org.pluchon.forum.config.ForumSearchProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.pluchon.forum.common.utils.SearchKeywordHelper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;
    private static final int RAG_TEXT_TRUNCATE = 1200;

    // 相关度阈值统一由配置提供，见 forum.search.*
    @Autowired
    private ForumSearchProperties forumSearchProperties;

    private String normalizeSearchKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEARCH_KEYWORD_EMPTY));
        }
        String kw = keyword.trim();
        if (kw.length() > ForumBusinessConstants.SEARCH_KEYWORD_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "搜索关键词过长"));
        }
        return kw;
    }
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ForumArticleAiFeatureMapper articleAiFeatureMapper;

    @Autowired
    private ContentUserLookupService userInternalLookupService;

    @Autowired
    private ContentAiGatewayService aiHubService;

    @Autowired
    private ArticleSearchIndexService articleSearchIndexService;

    @Autowired
    private ContentFollowLookupService userFollowService;

    @Autowired
    private org.pluchon.forum.service.interfaces.article.ArticleMediaService articleMediaService;

    @Override
    public SearchArticleResponse searchArticles(String keyword, Integer pageNum, Integer pageSize,
                                                boolean preferAiRag, Long viewerId) {
        String kw = normalizeSearchKeyword(keyword);
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);

        if (!preferAiRag) {
            return searchArticlesTraditionally(kw, p, s);
        }

        return searchArticlesByAiRag(kw, p, s, viewerId);
    }

    // AI 搜索是全站唯一没有配额的 AI 入口，而一次查询最多打三次 Python，
    // 且搜不到结果的查询三次全跑。命中排序缓存（翻页）不算配额
    private void assertAiSearchQuota(Long viewerId) {
        if (viewerId == null) {
            return;
        }
        if (!RedisWindowCounter.tryAcquire(stringRedisTemplate,
                Constant.REDIS_KEY_AI_SEARCH_MINUTE + viewerId,
                Constant.AI_SEARCH_MAX_PER_MINUTE, Constant.REDIS_TTL_AI_SEARCH_MINUTE)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_SEARCH_RATE_LIMIT));
        }
        if (!RedisWindowCounter.tryAcquire(stringRedisTemplate,
                Constant.REDIS_KEY_AI_SEARCH_DAY + viewerId,
                Constant.AI_SEARCH_MAX_PER_DAY, Constant.REDIS_TTL_AI_SEARCH_DAY)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_SEARCH_DAILY_LIMIT));
        }
    }

    // 排序结果缓存：同一个人搜同一个词，排序是确定的，翻页不该把整条 RAG 流水线重跑一遍。
    // 只缓存 ID 顺序，可见性仍由 retainPublishedArticleIds 每次现查，避免命中缓存期间展示已下架的帖子
    private String aiRankCacheKey(Long viewerId, String query) {
        if (viewerId == null || !StringUtils.hasText(query)) {
            return null;
        }
        return Constant.REDIS_KEY_AI_SEARCH_RANK + viewerId + ":" + Integer.toHexString(query.hashCode());
    }

    private List<Long> readAiRankCache(String key) {
        if (key == null) {
            return List.of();
        }
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cached)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : cached.split(",")) {
            try {
                ids.add(Long.valueOf(part));
            } catch (NumberFormatException ignored) {
                // 脏数据直接跳过，当作没命中
            }
        }
        return ids;
    }

    private void writeAiRankCache(String key, List<Long> ids) {
        if (key == null || ids == null || ids.isEmpty()) {
            return;
        }
        String joined = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        stringRedisTemplate.opsForValue().set(key, joined,
                Constant.REDIS_TTL_AI_SEARCH_RANK, java.util.concurrent.TimeUnit.SECONDS);
    }

    private SearchArticleResponse searchArticlesTraditionally(String keyword, int page, int size) {
        Set<Long> candidateIds = new LinkedHashSet<>(articleSearchIndexService.searchPublishedIds(
                keyword, Constant.SEARCH_INVERTED_MAX_RESULTS));
        List<Article> textMatches = articleMapper.selectPage(
                new Page<>(1, Constant.SEARCH_INVERTED_MAX_RESULTS, false),
                buildDbFuzzyArticleQuery(keyword)).getRecords();
        textMatches.forEach(article -> candidateIds.add(article.getId()));

        var authorMatches = userInternalLookupService.searchByKeyword(
                keyword, 1, Constant.SEARCH_RAG_CANDIDATE_LIMIT);
        if (authorMatches != null && authorMatches.getRecords() != null
                && !authorMatches.getRecords().isEmpty()) {
            List<Long> authorIds = authorMatches.getRecords().stream()
                    .map(UserInternalVO::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            List<Article> authorArticles = articleMapper.selectPage(
                    new Page<>(1, Constant.SEARCH_INVERTED_MAX_RESULTS, false),
                    publishedArticleQuery().in(Article::getUserId, authorIds)
                            .orderByDesc(Article::getUpdateTime)
                            .orderByDesc(Article::getId)).getRecords();
            authorArticles.forEach(article -> candidateIds.add(article.getId()));
        }
        if (candidateIds.isEmpty()) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY,
                    keyword, emptyPage(page, size));
        }

        List<Article> candidates = loadPublishedArticlesInOrder(new ArrayList<>(candidateIds));
        Map<Long, UserInternalVO> authors = loadAuthors(candidates);
        candidates.sort(Comparator
                .comparingInt((Article article) -> traditionalArticleRank(
                        article, authors.get(article.getUserId()), keyword))
                .thenComparing(Article::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Article::getId, Comparator.reverseOrder()));
        List<Long> rankedIds = candidates.stream().map(Article::getId).toList();
        PageResult<ArticleListResponse> result = pageRankedArticles(rankedIds, page, size);
        String source = textMatches.isEmpty() ? Constant.SEARCH_SOURCE_DB : Constant.SEARCH_SOURCE_INV;
        return new SearchArticleResponse(source, keyword, result);
    }

    private LambdaQueryWrapper<Article> publishedArticleQuery() {
        return new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode());
    }

    private int traditionalArticleRank(Article article, UserInternalVO author, String keyword) {
        String query = safeLower(keyword);
        String title = safeLower(article.getTitle());
        String nickname = author == null ? "" : safeLower(author.getNickname());
        String content = safeLower(stripHtml(article.getContent()));
        if (title.equals(query)) {
            return 0;
        }
        if (title.startsWith(query) || title.contains(query)) {
            return 1;
        }
        if (nickname.equals(query)) {
            return 2;
        }
        if (nickname.contains(query)) {
            return 3;
        }
        return content.contains(query) ? 4 : 5;
    }

    private PageResult<ArticleListResponse> pageRankedArticles(
            List<Long> rankedIds, int page, int size) {
        long total = rankedIds.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= total) {
            return new PageResult<>(Collections.emptyList(), total, page, size,
                    (total + size - 1) / size, false);
        }
        int toIndex = Math.min(fromIndex + size, rankedIds.size());
        List<ArticleListResponse> records = buildListResponsesForRag(
                rankedIds.subList(fromIndex, toIndex));
        long pages = (total + size - 1) / size;
        return new PageResult<>(records, total, page, size, pages, toIndex < total);
    }

    // AI 语义搜索：先清洗噪声词，再从 DB 捞字面相关候选，hybrid_rank 打分；低分丢弃。 候选为空时再走向量兜底，且使用更高阈值，避免无关结果
    private SearchArticleResponse searchArticlesByAiRag(String kw, int p, int s, Long viewerId) {
        String query = SearchKeywordHelper.sanitizeAiSearchQuery(kw);
        if (!StringUtils.hasText(query)) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }
        String cacheKey = aiRankCacheKey(viewerId, query);
        List<Long> cachedIds = readAiRankCache(cacheKey);
        if (!cachedIds.isEmpty()) {
            return buildAiRagResponse(kw, cachedIds, p, s);
        }
        assertAiSearchQuota(viewerId);
        List<Long> rankedIds = new ArrayList<>();

        List<Long> candidateIds = articleSearchIndexService.searchPublishedIds(
                query, Constant.SEARCH_RAG_CANDIDATE_LIMIT);
        List<Article> candidates;
        if (!candidateIds.isEmpty()) {
            candidates = loadPublishedArticlesInOrder(candidateIds);
        } else {
            candidates = articleMapper.selectPage(new Page<>(1, Constant.SEARCH_RAG_CANDIDATE_LIMIT, false),
                buildRagCandidateArticleQuery(query)).getRecords();
        }
        if (candidates != null && !candidates.isEmpty()) {
            Map<Long, String> summaries = loadReadySummaries(candidates);
            Map<Long, UserInternalVO> authors = loadAuthors(candidates);
            List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
            for (Article a : candidates) {
                Map<String, Object> item = new HashMap<>(5);
                item.put("articleId", a.getId());
                item.put("title", a.getTitle());
                item.put("summary", summaries.getOrDefault(a.getId(), ""));
                UserInternalVO author = authors.get(a.getUserId());
                item.put("authorNickname", author == null ? "" : author.getNickname());
                item.put("text", buildRagCandidateText(a));
                payload.add(item);
            }
            rankedIds = extractArticleHitIds(aiHubService.ragArticleVectorRanked(ragRequest(query, payload)),
                    forumSearchProperties.getArticleHybridMinScore());
        }

        if (rankedIds.isEmpty()) {
            rankedIds = extractArticleHitIds(aiHubService.ragArticleVectorRanked(
                    ragRequest(query, Collections.emptyList())),
                    forumSearchProperties.getArticleVectorMinScore());
        }

        if (rankedIds.isEmpty()) {
            rankedIds = articleIdsByHighSimilarityAuthors(query);
        }

        if (rankedIds.isEmpty()) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }
        if (rankedIds.size() > Constant.SEARCH_RAG_MAX_RESULTS) {
            rankedIds = rankedIds.subList(0, Constant.SEARCH_RAG_MAX_RESULTS);
        }
        writeAiRankCache(cacheKey, rankedIds);
        return buildAiRagResponse(kw, rankedIds, p, s);
    }

    // 可见性每次现查：缓存里只有 ID 顺序，5 分钟内被下架的帖子不能因为命中缓存就漏出去
    private SearchArticleResponse buildAiRagResponse(String kw, List<Long> rankedIds, int p, int s) {
        List<Long> visibleIds = retainPublishedArticleIds(rankedIds);
        if (visibleIds.isEmpty()) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }
        long total = visibleIds.size();
        int fromIdx = (p - 1) * s;
        int toIdx = Math.min(fromIdx + s, visibleIds.size());
        List<Long> pageSlice = fromIdx >= visibleIds.size() ? Collections.emptyList()
                : visibleIds.subList(fromIdx, toIdx);
        List<ArticleListResponse> records = buildListResponsesForRag(pageSlice);
        long pages = (total + s - 1) / s;
        PageResult<ArticleListResponse> pageResult = new PageResult<>(
                records, total, p, s, pages, toIdx < visibleIds.size());
        return new SearchArticleResponse(Constant.SEARCH_SOURCE_RAG, kw, pageResult);
    }

    private AiRagSearchRequest ragRequest(String query, List<Map<String, Object>> candidates) {
        AiRagSearchRequest request = new AiRagSearchRequest();
        request.setQuery(query);
        request.setCandidates(candidates);
        return request;
    }

    // 正文未命中时，只接受高相似度作者语义命中，避免把普通用户检索扩散为无关帖子
    private List<Long> articleIdsByHighSimilarityAuthors(String keyword) {
        List<Long> authorIds = extractUserHitIds(aiHubService.ragUserVectorRanked(keyword)
        );
        if (authorIds.isEmpty()) {
            return Collections.emptyList();
        }
        return articleMapper.selectPage(new Page<>(1, Constant.SEARCH_RAG_MAX_RESULTS, false), new LambdaQueryWrapper<Article>()
                        .in(Article::getUserId, authorIds)
                        .ne(Article::getDeleteState, DELETE_TRUE)
                        .ne(Article::getState, STATE_FORBIDDEN)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                        .orderByDesc(Article::getUpdateTime)
                        .select(Article::getId))
                .getRecords()
                .stream()
                .map(Article::getId)
                .toList();
    }

    @Override
    public SearchUserResponse searchUsers(String keyword, Integer pageNum, Integer pageSize,
                                          boolean preferAiRag, Long viewerId) {
        String kw = normalizeSearchKeyword(keyword);
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);

        // content 无 user 表：字面 / AI 用户搜索一律走 auth Feign 或向量 + Feign 回表
        if (!preferAiRag) {
            return searchUsersByDbRemote(kw, p, s, viewerId);
        }
        return searchUsersByAiRagRemote(kw, p, s, viewerId);
    }

    // 微服务模式：字面搜索走 auth Feign，再组装关注态
    private SearchUserResponse searchUsersByDbRemote(String kw, int p, int s, Long viewerId) {
        var remote = userInternalLookupService.searchByKeyword(kw, p, s);
        if (remote == null || remote.getRecords() == null || remote.getRecords().isEmpty()) {
            return new SearchUserResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyUserPage(p, s));
        }
        List<UserInternalVO> users = remote.getRecords().stream()
                .filter(Objects::nonNull)
                .toList();
        PageResult<SearchUserItemVO> pageResult = new PageResult<>(
                buildSearchUserItems(users, viewerId),
                remote.getTotal(),
                p,
                s,
                remote.getPages(),
                remote.isHasNext());
        return new SearchUserResponse(Constant.SEARCH_SOURCE_DB, kw, pageResult);
    }

    // 微服务模式：无 user 表，仅向量检索 + Feign 批量回表
    private SearchUserResponse searchUsersByAiRagRemote(String kw, int p, int s, Long viewerId) {
        String query = SearchKeywordHelper.sanitizeAiSearchQuery(kw);
        if (!StringUtils.hasText(query)) {
            return new SearchUserResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyUserPage(p, s));
        }
        List<RagUserVectorHitVO> hits = aiHubService.ragUserVectorRanked(query);
        List<Long> rankedIds = rankUsersByFollowersAndSemantic(query, hits, viewerId);
        if (rankedIds.isEmpty()) {
            return new SearchUserResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyUserPage(p, s));
        }
        if (rankedIds.size() > Constant.SEARCH_RAG_MAX_RESULTS) {
            rankedIds = rankedIds.subList(0, Constant.SEARCH_RAG_MAX_RESULTS);
        }
        long total = rankedIds.size();
        int fromIdx = (p - 1) * s;
        int toIdx = Math.min(fromIdx + s, rankedIds.size());
        List<Long> pageSlice = fromIdx >= rankedIds.size() ? Collections.emptyList()
                : rankedIds.subList(fromIdx, toIdx);
        List<SearchUserItemVO> records = buildSearchUserListForRag(pageSlice, viewerId);
        long pages = (total + s - 1) / s;
        PageResult<SearchUserItemVO> pageResult = new PageResult<>(
                records, total, p, s, pages, toIdx < rankedIds.size());
        return new SearchUserResponse(Constant.SEARCH_SOURCE_RAG, kw, pageResult);
    }

    @Override
    public SearchArticleResponse searchCreatorArticles(Long creatorUserId, String keyword, Integer status,
                                                       Integer pageNum, Integer pageSize, boolean preferAiRag) {
        String kw = normalizeSearchKeyword(keyword);
        int page = PageUtils.getValidPageNum(pageNum);
        int size = PageUtils.getValidPageSize(pageSize);
        LambdaQueryWrapper<Article> baseQuery = creatorArticleBaseQuery(creatorUserId, status);
        if (!preferAiRag) {
            baseQuery.and(wrapper -> applyTextLike(wrapper, keywordTerms(kw),
                    Article::getTitle, Article::getContent))
                    .orderByDesc(Article::getUpdateTime)
                    .orderByDesc(Article::getId);
            Page<Article> result = articleMapper.selectPage(PageUtils.getPage(page, size), baseQuery);
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_DB, kw, wrap(result, page, size, kw));
        }
        // 与社区 AI 同源：清洗 → 字面候选 → hybrid → 向量兜底，最后收窄到本人帖
        return searchCreatorArticlesByAiRag(creatorUserId, kw, status, page, size);
    }

    // 创作中心 AI：社区同阈值/同流水线，仅候选与结果限定本人（可含草稿等状态筛选）
    private SearchArticleResponse searchCreatorArticlesByAiRag(
            Long creatorUserId, String kw, Integer status, int page, int size) {
        String query = SearchKeywordHelper.sanitizeAiSearchQuery(kw);
        if (!StringUtils.hasText(query)) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(page, size));
        }
        // 优先字面相关本人帖；没有再退回最近本人帖做语义打分（避免旧帖挤出候选）
        List<Article> candidates = articleMapper.selectPage(
                new Page<>(1, Constant.SEARCH_RAG_CANDIDATE_LIMIT, false),
                creatorArticleBaseQuery(creatorUserId, status)
                        .and(wrapper -> applyTextLike(wrapper, keywordTerms(query),
                                Article::getTitle, Article::getContent))
                        .orderByDesc(Article::getUpdateTime)
                        .orderByDesc(Article::getId)).getRecords();
        boolean literalCandidate = candidates != null && !candidates.isEmpty();
        if (!literalCandidate) {
            candidates = articleMapper.selectPage(
                    new Page<>(1, Constant.SEARCH_RAG_CANDIDATE_LIMIT, false),
                    creatorArticleBaseQuery(creatorUserId, status)
                            .orderByDesc(Article::getUpdateTime)
                            .orderByDesc(Article::getId)).getRecords();
        }
        List<Long> rankedIds = new ArrayList<>();
        Map<Long, Article> byId = new HashMap<>();
        if (candidates != null && !candidates.isEmpty()) {
            for (Article article : candidates) {
                byId.put(article.getId(), article);
            }
            Map<Long, String> summaries = loadReadySummaries(candidates);
            List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
            for (Article article : candidates) {
                Map<String, Object> item = new HashMap<>(5);
                item.put("articleId", article.getId());
                item.put("title", article.getTitle());
                item.put("summary", summaries.getOrDefault(article.getId(), ""));
                item.put("authorNickname", "");
                item.put("text", buildRagCandidateText(article));
                payload.add(item);
            }
            rankedIds = extractArticleHitIds(
                    aiHubService.ragArticleVectorRanked(ragRequest(query, payload)),
                    forumSearchProperties.getArticleHybridMinScore());
        }
        if (rankedIds.isEmpty()) {
            List<Long> vectorIds = extractArticleHitIds(
                    aiHubService.ragArticleVectorRanked(ragRequest(query, Collections.emptyList())),
                    forumSearchProperties.getArticleVectorMinScore());
            rankedIds = retainCreatorArticleIds(vectorIds, creatorUserId, status);
            if (!rankedIds.isEmpty()) {
                List<Article> vectorArticles = articleMapper.selectList(
                        creatorArticleBaseQuery(creatorUserId, status).in(Article::getId, rankedIds));
                for (Article article : vectorArticles) {
                    byId.put(article.getId(), article);
                }
            }
        }
        // 字面命中保底：标题/正文已命中的本人帖不得因向量分被丢掉
        if (literalCandidate) {
            rankedIds = mergeCreatorLiteralThenSemantic(candidates, rankedIds, query);
            for (Article article : candidates) {
                byId.putIfAbsent(article.getId(), article);
            }
        }
        if (rankedIds.isEmpty()) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(page, size));
        }
        Map<Long, Integer> semanticOrder = new HashMap<>();
        for (int index = 0; index < rankedIds.size(); index++) {
            semanticOrder.put(rankedIds.get(index), index);
        }
        rankedIds = new ArrayList<>(rankedIds);
        rankedIds.sort(Comparator
                .comparingInt((Long id) -> creatorTitleMatchRank(byId.get(id), query))
                .thenComparingInt(id -> semanticOrder.getOrDefault(id, Integer.MAX_VALUE)));
        if (rankedIds.size() > Constant.SEARCH_RAG_MAX_RESULTS) {
            rankedIds = rankedIds.subList(0, Constant.SEARCH_RAG_MAX_RESULTS);
        }
        long total = rankedIds.size();
        int fromIndex = Math.min((page - 1) * size, rankedIds.size());
        int toIndex = Math.min(fromIndex + size, rankedIds.size());
        List<ArticleListResponse> records = buildListResponsesForCreator(
                rankedIds.subList(fromIndex, toIndex), creatorUserId, status);
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new SearchArticleResponse(Constant.SEARCH_SOURCE_RAG, kw,
                new PageResult<>(records, total, page, size, pages, toIndex < total));
    }

    private LambdaQueryWrapper<Article> creatorArticleBaseQuery(Long creatorUserId, Integer status) {
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, creatorUserId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN);
        if (status != null) {
            query.eq(Article::getStatus, status);
        }
        return query;
    }

    private List<Long> retainCreatorArticleIds(List<Long> ids, Long creatorUserId, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> rows = articleMapper.selectList(creatorArticleBaseQuery(creatorUserId, status)
                .in(Article::getId, ids)
                .select(Article::getId));
        Set<Long> allowed = rows.stream().map(Article::getId).collect(Collectors.toSet());
        List<Long> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (allowed.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private List<Long> mergeCreatorLiteralThenSemantic(
            List<Article> literalCandidates, List<Long> semanticIds, String query) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>();
        List<Article> sortedLiteral = new ArrayList<>(literalCandidates);
        sortedLiteral.sort(Comparator.comparingInt(article -> creatorTitleMatchRank(article, query)));
        for (Article article : sortedLiteral) {
            if (article.getId() != null) {
                merged.add(article.getId());
            }
        }
        if (semanticIds != null) {
            merged.addAll(semanticIds);
        }
        return new ArrayList<>(merged);
    }

    private int creatorTitleMatchRank(Article article, String keyword) {
        if (article == null) {
            return 4;
        }
        String title = article.getTitle() == null ? "" : article.getTitle().trim();
        if (title.equals(keyword)) {
            return 0;
        }
        if (title.startsWith(keyword)) {
            return 1;
        }
        if (title.contains(keyword)) {
            return 2;
        }
        return 3;
    }

    private List<ArticleListResponse> buildListResponsesForCreator(
            List<Long> ids, Long creatorUserId, Integer status) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids)
                .eq(Article::getUserId, creatorUserId)
                .ne(Article::getDeleteState, DELETE_TRUE);
        if (status != null) {
            query.eq(Article::getStatus, status);
        }
        Map<Long, Article> byId = articleMapper.selectList(query).stream()
                .collect(Collectors.toMap(Article::getId, article -> article));
        List<ArticleListResponse> list = ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(this::toListResponse).toList();
        fillImageMeta(list);
        return list;
    }

    private List<Long> rankUsersByFollowersAndSemantic(
            String keyword, List<RagUserVectorHitVO> hits, Long viewerId) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Double> semanticScores = new HashMap<>();
        for (RagUserVectorHitVO hit : hits) {
            if (hit.getUserId() == null || hit.getScore() == null
                    || hit.getScore() < forumSearchProperties.getUserVectorMinScore()) {
                continue;
            }
            semanticScores.put(hit.getUserId(), hit.getScore());
        }
        if (semanticScores.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserInternalVO> users = userInternalLookupService.listByIds(
                new ArrayList<>(semanticScores.keySet()));
        Map<Long, UserFollowStatsVO> stats = userFollowService.getBatchStats(
                semanticScores.keySet(), viewerId);
        double maxLogFollowers = users.stream()
                .map(user -> stats.get(user.getId()))
                .filter(java.util.Objects::nonNull)
                .map(UserFollowStatsVO::getFollowerCount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(count -> Math.log1p(Math.max(0L, count)))
                .max()
                .orElse(1.0);
        final double followerScale = maxLogFollowers <= 0 ? 1.0 : maxLogFollowers;
        String normalized = keyword.trim().toLowerCase(java.util.Locale.ROOT);
        return users.stream()
                .filter(user -> user.getState() == null || user.getState() != STATE_FORBIDDEN)
                .sorted(Comparator
                        .comparing((UserInternalVO user) -> normalized.equals(
                                safeLower(user.getNickname()))).reversed()
                        .thenComparing((UserInternalVO user) -> {
                            UserFollowStatsVO row = stats.get(user.getId());
                            long followers = row == null || row.getFollowerCount() == null
                                    ? 0L : row.getFollowerCount();
                            double followerScore = Math.log1p(Math.max(0L, followers)) / followerScale;
                            return followerScore * 0.70
                                    + semanticScores.getOrDefault(user.getId(), 0.0) * 0.30;
                        }, Comparator.reverseOrder())
                        .thenComparing(UserInternalVO::getId, Comparator.reverseOrder()))
                .map(UserInternalVO::getId)
                .toList();
    }

    private Map<Long, String> loadReadySummaries(List<Article> articles) {
        List<Long> articleIds = articles.stream().map(Article::getId).toList();
        List<ForumArticleAiFeature> rows = articleAiFeatureMapper.selectList(
                new LambdaQueryWrapper<ForumArticleAiFeature>()
                        .in(ForumArticleAiFeature::getArticleId, articleIds)
                        .eq(ForumArticleAiFeature::getSummaryStatus, (byte) 2)
                        .ne(ForumArticleAiFeature::getDeleteState, DELETE_TRUE)
                        .select(ForumArticleAiFeature::getArticleId,
                                ForumArticleAiFeature::getSummaryText));
        return rows.stream()
                .filter(row -> StringUtils.hasText(row.getSummaryText()))
                .collect(Collectors.toMap(ForumArticleAiFeature::getArticleId,
                        ForumArticleAiFeature::getSummaryText, (left, right) -> right));
    }

    private Map<Long, UserInternalVO> loadAuthors(List<Article> articles) {
        List<Long> userIds = articles.stream().map(Article::getUserId).distinct().toList();
        return userInternalLookupService.listByIds(userIds).stream()
                .collect(Collectors.toMap(UserInternalVO::getId, user -> user,
                        (left, right) -> left));
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private List<Long> extractArticleHitIds(List<RagArticleVectorHitVO> ranked, double minScore) {
        if (ranked == null || ranked.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (RagArticleVectorHitVO row : ranked) {
            double score = row.getScore() != null ? row.getScore() : 0.0;
            if (score < minScore || row.getArticleId() == null) {
                continue;
            }
            if (seen.add(row.getArticleId())) {
                out.add(row.getArticleId());
            }
        }
        return out;
    }

    private List<Long> extractUserHitIds(List<RagUserVectorHitVO> ranked) {
        if (ranked == null || ranked.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (RagUserVectorHitVO row : ranked) {
            double score = row.getScore() != null ? row.getScore() : 0.0;
            if (score < forumSearchProperties.getArticleAuthorVectorMinScore() || row.getUserId() == null) {
                continue;
            }
            if (seen.add(row.getUserId())) {
                out.add(row.getUserId());
            }
        }
        return out;
    }

    // RAG 索引属于事务外副作用，展示前必须以数据库公开状态为准，避免撤稿或回退草稿的帖子被旧向量命中
    private List<Long> retainPublishedArticleIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .select(Article::getId));
        Set<Long> publishedIds = rows.stream()
                .map(Article::getId)
                .collect(Collectors.toSet());
        List<Long> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (publishedIds.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    // AI 搜索候选：标题或正文包含扩展检索词
    private LambdaQueryWrapper<Article> buildRagCandidateArticleQuery(String kw) {
        return new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> applyTextLike(w, keywordTerms(kw), Article::getTitle, Article::getContent))
                .orderByDesc(Article::getUpdateTime);
    }

    private String buildRagCandidateText(Article article) {
        String title = article.getTitle() == null ? "" : article.getTitle().trim();
        String body = stripHtml(article.getContent());
        if (body.length() > RAG_TEXT_TRUNCATE) {
            body = body.substring(0, RAG_TEXT_TRUNCATE);
        }
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank()) {
            sb.append("标题: ").append(title).append('\n');
        }
        if (!body.isBlank()) {
            sb.append("正文:\n").append(body);
        }
        return sb.toString().trim();
    }

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    
    private PageResult<ArticleListResponse> wrap(Page<Article> page, int p, int s, String kw) {
        List<String> terms = keywordTerms(kw);
        List<Article> sorted = new ArrayList<>(page.getRecords());
        sorted.sort((a, b) -> Integer.compare(
                SearchKeywordHelper.literalRelevanceScore(b.getTitle(), stripHtml(b.getContent()), terms),
                SearchKeywordHelper.literalRelevanceScore(a.getTitle(), stripHtml(a.getContent()), terms)));
        List<ArticleListResponse> records = sorted.stream()
                .map(this::toListResponse).collect(Collectors.toList());
        fillImageMeta(records);
        return new PageResult<>(records, page.getTotal(), p, s, page.getPages(), page.hasNext());
    }

    private PageResult<ArticleListResponse> emptyPage(int p, int s) {
        return new PageResult<>(Collections.emptyList(), 0L, p, s, 0L, false);
    }

    // 普通搜索：标题或正文包含关键词 模糊 LIKE
    private LambdaQueryWrapper<Article> buildDbFuzzyArticleQuery(String kw) {
        List<String> terms = keywordTerms(kw);
        return new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> applyTextLike(w, terms, Article::getTitle, Article::getContent))
                .orderByDesc(Article::getUpdateTime);
    }

    // MyBatis-Plus 的 like 会参数化，没有注入风险；但 % 和 _ 仍会被 MySQL 当通配符。
    // 搜一个 % 就能匹配全表，把候选上限那么多条捞出来在内存里排序
    private String escapeLikeWildcards(String term) {
        if (term == null) {
            return null;
        }
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @SafeVarargs
    private <T> void applyTextLike(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> w,
            List<String> terms,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?>... columns) {
        boolean first = true;
        for (String term : terms) {
            String safe = escapeLikeWildcards(term);
            for (var col : columns) {
                if (first) {
                    w.like(col, safe);
                    first = false;
                } else {
                    w.or().like(col, safe);
                }
            }
        }
    }

    // 原词 + 字面分词 控制词数，减轻 MySQL / AI 压力；语义联想交由 AI 检索负责
    private List<String> keywordTerms(String kw) {
        return SearchKeywordHelper.expandTerms(kw);
    }

    private PageResult<ArticleListResponse> pageArticlesByIds(List<Long> orderedIds, String kw, int p, int s) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return emptyPage(p, s);
        }
        long total = orderedIds.size();
        int fromIdx = (p - 1) * s;
        if (fromIdx >= orderedIds.size()) {
            return emptyPage(p, s);
        }
        int toIdx = Math.min(fromIdx + s, orderedIds.size());
        List<Long> pageIds = orderedIds.subList(fromIdx, toIdx);
        List<ArticleListResponse> records = buildListResponsesForPublished(pageIds, kw);
        long pages = (total + s - 1) / s;
        return new PageResult<>(records, total, p, s, pages, toIdx < orderedIds.size());
    }

    private List<Article> loadPublishedArticlesInOrder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, orderedIds)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
        Map<Long, Article> byId = new HashMap<>(rows.size() * 2);
        for (Article a : rows) {
            byId.put(a.getId(), a);
        }
        List<Article> out = new ArrayList<>(orderedIds.size());
        for (Long id : orderedIds) {
            Article a = byId.get(id);
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }

    private List<ArticleListResponse> buildListResponsesForPublished(List<Long> ids, String kw) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> terms = keywordTerms(kw);
        List<Article> rows = loadPublishedArticlesInOrder(ids);
        rows.sort((a, b) -> Integer.compare(
                SearchKeywordHelper.literalRelevanceScore(b.getTitle(), stripHtml(b.getContent()), terms),
                SearchKeywordHelper.literalRelevanceScore(a.getTitle(), stripHtml(a.getContent()), terms)));
        List<ArticleListResponse> list = rows.stream().map(this::toListResponse).collect(Collectors.toList());
        fillImageMeta(list);
        return list;
    }

    // RAG 命中 ID 回表；展示前仍以数据库公开状态为准，避免旧向量索引泄露未发布帖子
    private List<ArticleListResponse> buildListResponsesForRag(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
        Map<Long, Article> byId = new HashMap<>(rows.size() * 2);
        for (Article a : rows) {
            byId.put(a.getId(), a);
        }
        List<ArticleListResponse> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Article a = byId.get(id);
            if (a == null) {
                log.warn("RAG 命中帖子在库中不可用 articleId={}", id);
                continue;
            }
            out.add(toListResponse(a));
        }
        fillImageMeta(out);
        return out;
    }

    private ArticleListResponse toListResponse(Article article) {
        ArticleListResponse vo = new ArticleListResponse();
        vo.setArticle(article);
        try {
            UserInternalVO u = userInternalLookupService.getUserInfoById(article.getUserId());
            if (u != null) vo.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(u));
        } catch (ApplicationException e) {
            log.warn("搜索结果中作者 {} 已不可用, 跳过用户信息", article.getUserId());
        }
        return vo;
    }

    private void fillImageMeta(List<ArticleListResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> articleIds = records.stream()
                .map(ArticleListResponse::getArticle)
                .filter(java.util.Objects::nonNull)
                .map(Article::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (articleIds.isEmpty()) {
            return;
        }
        Map<Long, Integer> imageCounts = articleMediaService.countImagesByArticleIds(articleIds);
        Map<Long, String> firstImageUrls = articleMediaService.firstImageUrlByArticleIds(articleIds);
        for (ArticleListResponse response : records) {
            Article article = response.getArticle();
            if (article == null || article.getId() == null) {
                continue;
            }
            response.setImageCount(imageCounts.getOrDefault(article.getId(), 0));
            response.setFirstImageUrl(firstImageUrls.get(article.getId()));
        }
    }

    private PageResult<SearchUserItemVO> emptyUserPage(int p, int s) {
        return new PageResult<>(Collections.emptyList(), 0L, p, s, 0L, false);
    }

    private List<SearchUserItemVO> buildSearchUserListForRag(List<Long> ids, Long viewerId) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserInternalVO> rows = userInternalLookupService.listByIds(ids).stream()
                .filter(u -> u.getState() == null || u.getState() != STATE_FORBIDDEN)
                .toList();
        Map<Long, UserInternalVO> byId = new HashMap<>(rows.size() * 2);
        for (UserInternalVO u : rows) {
            byId.put(u.getId(), u);
        }
        List<UserInternalVO> orderedUsers = new ArrayList<>(ids.size());
        for (Long id : ids) {
            UserInternalVO u = byId.get(id);
            if (u == null) {
                continue;
            }
            orderedUsers.add(u);
        }
        return buildSearchUserItems(orderedUsers, viewerId);
    }

    private List<SearchUserItemVO> buildSearchUserItems(List<UserInternalVO> users, Long viewerId) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = users.stream().map(UserInternalVO::getId).toList();
        Map<Long, UserFollowStatsVO> stats = userFollowService.getBatchStats(userIds, viewerId);
        List<SearchUserItemVO> out = new ArrayList<>(users.size());
        for (UserInternalVO user : users) {
            out.add(SearchUserConverter.toItem(user, stats.get(user.getId())));
        }
        return out;
    }
}
