package org.example.forumdemo.service.impl.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.article.ArticleListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.search.SearchArticleResponse;
import org.example.forumdemo.entity.vo.search.SearchUserResponse;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.ai.AiHubService;
import org.example.forumdemo.service.interfaces.search.ArticleSearchIndexService;
import org.example.forumdemo.service.interfaces.search.SearchService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.example.forumdemo.common.utils.SearchKeywordHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;
    /** AI 搜索：候选集 hybrid_rank 最低分 */
    private static final double ARTICLE_AI_HYBRID_MIN_SCORE = 0.22;
    /** AI 搜索：无字面候选时，纯向量兜底最低分（更高，减少误召回） */
    private static final double ARTICLE_AI_VECTOR_MIN_SCORE = 0.30;
    /** AI 用户搜索：候选集 hybrid_rank 最低分 */
    private static final double USER_AI_HYBRID_MIN_SCORE = 0.22;
    /** AI 用户搜索：纯向量兜底最低分 */
    private static final double USER_AI_VECTOR_MIN_SCORE = 0.38;
    private static final int RAG_TEXT_TRUNCATE = 1200;
    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AiHubService aiHubService;

    @Autowired
    private ArticleSearchIndexService articleSearchIndexService;

    @Override
    public SearchArticleResponse searchArticles(String keyword, Integer pageNum, Integer pageSize, boolean preferAiRag) {
        if (!StringUtils.hasText(keyword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEARCH_KEYWORD_EMPTY));
        }
        String kw = keyword.trim();
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);

        if (!preferAiRag) {
            List<Long> invIds = articleSearchIndexService.searchPublishedIds(
                    kw, Constant.SEARCH_INVERTED_MAX_RESULTS);
            if (!invIds.isEmpty()) {
                return new SearchArticleResponse(
                        Constant.SEARCH_SOURCE_INV, kw, pageArticlesByIds(invIds, kw, p, s));
            }
            Page<Article> page = PageUtils.getPage(p, s);
            Page<Article> dbResult = articleMapper.selectPage(page, buildDbFuzzyArticleQuery(kw));
            if (dbResult.getRecords() != null && !dbResult.getRecords().isEmpty()) {
                return new SearchArticleResponse(Constant.SEARCH_SOURCE_DB, kw, wrap(dbResult, p, s, kw));
            }
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }

        return searchArticlesByAiRag(kw, p, s);
    }

    /**
     * AI 语义搜索：先从 DB 捞字面相关候选，再 hybrid_rank 打分；低分丢弃。
     * 候选为空时再走向量兜底，且使用更高阈值，避免无关结果。
     */
    private SearchArticleResponse searchArticlesByAiRag(String kw, int p, int s) {
        List<Long> rankedIds = new ArrayList<>();

        List<Long> candidateIds = articleSearchIndexService.searchPublishedIds(
                kw, Constant.SEARCH_RAG_CANDIDATE_LIMIT);
        List<Article> candidates;
        if (!candidateIds.isEmpty()) {
            candidates = loadPublishedArticlesInOrder(candidateIds);
        } else {
            candidates = articleMapper.selectPage(new Page<>(1, Constant.SEARCH_RAG_CANDIDATE_LIMIT, false),
                buildRagCandidateArticleQuery(kw)).getRecords();
        }
        if (candidates != null && !candidates.isEmpty()) {
            List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
            for (Article a : candidates) {
                Map<String, Object> item = new HashMap<>(2);
                item.put("articleId", a.getId());
                item.put("text", buildRagCandidateText(a));
                payload.add(item);
            }
            rankedIds = extractRankedIds(AiAuditUtils.ragSearchArticlesRanked(kw, payload), ARTICLE_AI_HYBRID_MIN_SCORE);
        }

        if (rankedIds.isEmpty()) {
            rankedIds = extractRankedIds(aiHubService.ragArticleVectorRanked(kw, Collections.emptyList()),
                    ARTICLE_AI_VECTOR_MIN_SCORE);
        }

        if (rankedIds.isEmpty()) {
            log.info("AI 帖子语义搜索未命中 keyword={}", kw);
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }
        if (rankedIds.size() > Constant.SEARCH_RAG_MAX_RESULTS) {
            rankedIds = rankedIds.subList(0, Constant.SEARCH_RAG_MAX_RESULTS);
        }
        long total = rankedIds.size();
        int fromIdx = (p - 1) * s;
        int toIdx = Math.min(fromIdx + s, rankedIds.size());
        List<Long> pageSlice = fromIdx >= rankedIds.size() ? Collections.emptyList()
                : rankedIds.subList(fromIdx, toIdx);
        List<ArticleListResponse> records = buildListResponsesForRag(pageSlice);
        long pages = (total + s - 1) / s;
        PageResult<ArticleListResponse> pageResult = new PageResult<>(
                records, total, p, s, pages, toIdx < rankedIds.size());
        return new SearchArticleResponse(Constant.SEARCH_SOURCE_RAG, kw, pageResult);
    }

    @Override
    public SearchUserResponse searchUsers(String keyword, Integer pageNum, Integer pageSize, boolean preferAiRag) {
        if (!StringUtils.hasText(keyword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEARCH_KEYWORD_EMPTY));
        }
        String kw = keyword.trim();
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);

        if (!preferAiRag) {
            Page<User> page = PageUtils.getPage(p, s);
            Page<User> dbResult = userMapper.selectPage(page, buildDbFuzzyUserQuery(kw));
            if (dbResult.getRecords() != null && !dbResult.getRecords().isEmpty()) {
                return new SearchUserResponse(Constant.SEARCH_SOURCE_DB, kw, wrapUsers(dbResult, p, s, kw));
            }
            return new SearchUserResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyUserPage(p, s));
        }

        return searchUsersByAiRag(kw, p, s);
    }

    /** AI 用户搜索：字面候选 + hybrid_rank 打分；低分丢弃；无候选再走向量兜底。 */
    private SearchUserResponse searchUsersByAiRag(String kw, int p, int s) {
        List<Long> rankedIds = new ArrayList<>();

        List<User> candidates = userMapper.selectPage(
                new Page<>(1, Constant.SEARCH_RAG_CANDIDATE_LIMIT, false),
                buildRagCandidateUserQuery(kw)).getRecords();
        if (candidates != null && !candidates.isEmpty()) {
            List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
            for (User u : candidates) {
                Map<String, Object> item = new HashMap<>(2);
                item.put("userId", u.getId());
                item.put("text", buildRagCandidateUserText(u));
                payload.add(item);
            }
            rankedIds = extractRankedIds(AiAuditUtils.ragSearchUsersRanked(kw, payload), USER_AI_HYBRID_MIN_SCORE);
        }

        if (rankedIds.isEmpty()) {
            rankedIds = extractRankedIds(aiHubService.ragUserVectorRanked(kw), USER_AI_VECTOR_MIN_SCORE);
        }

        if (rankedIds.isEmpty()) {
            log.info("AI 用户语义搜索未命中 keyword={}", kw);
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
        List<UserBriefVO> records = buildUserBriefListForRag(pageSlice);
        long pages = (total + s - 1) / s;
        PageResult<UserBriefVO> pageResult = new PageResult<>(
                records, total, p, s, pages, toIdx < rankedIds.size());
        return new SearchUserResponse(Constant.SEARCH_SOURCE_RAG, kw, pageResult);
    }

    private List<Long> extractRankedIds(List<Map<String, Object>> ranked, double minScore) {
        if (ranked == null || ranked.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : ranked) {
            double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
            if (score < minScore) {
                continue;
            }
            Object idObj = row.get("articleId");
            if (idObj == null) {
                idObj = row.get("userId");
            }
            if (idObj == null) {
                continue;
            }
            try {
                long id = Long.parseLong(String.valueOf(idObj));
                if (seen.add(id)) {
                    out.add(id);
                }
            } catch (NumberFormatException ignore) {
                // skip
            }
        }
        return out;
    }

    /** AI 搜索候选：标题或正文包含扩展检索词 */
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

    // ============ 内部工具 ============
    private PageResult<ArticleListResponse> wrap(Page<Article> page, int p, int s, String kw) {
        List<String> terms = keywordTerms(kw);
        List<Article> sorted = new ArrayList<>(page.getRecords());
        sorted.sort((a, b) -> Integer.compare(
                SearchKeywordHelper.literalRelevanceScore(b.getTitle(), stripHtml(b.getContent()), terms),
                SearchKeywordHelper.literalRelevanceScore(a.getTitle(), stripHtml(a.getContent()), terms)));
        List<ArticleListResponse> records = sorted.stream()
                .map(this::toListResponse).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), p, s, page.getPages(), page.hasNext());
    }

    private PageResult<ArticleListResponse> emptyPage(int p, int s) {
        return new PageResult<>(Collections.emptyList(), 0L, p, s, 0L, false);
    }

    /** 普通搜索：标题或正文包含关键词（模糊 LIKE） */
    private LambdaQueryWrapper<Article> buildDbFuzzyArticleQuery(String kw) {
        List<String> terms = keywordTerms(kw);
        return new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> applyTextLike(w, terms, Article::getTitle, Article::getContent))
                .orderByDesc(Article::getUpdateTime);
    }

    /** AI 搜索候选：昵称或用户名包含关键词 */
    private LambdaQueryWrapper<User> buildRagCandidateUserQuery(String kw) {
        List<String> terms = keywordTerms(kw);
        return new LambdaQueryWrapper<User>()
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN)
                .and(w -> applyTextLike(w, terms, User::getUsername, User::getNickname))
                .orderByDesc(User::getUpdateTime);
    }

    private String buildRagCandidateUserText(User user) {
        String nickname = user.getNickname() == null ? "" : user.getNickname().trim();
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String remark = user.getRemark() == null ? "" : user.getRemark().trim();
        if (remark.length() > 400) {
            remark = remark.substring(0, 400);
        }
        StringBuilder sb = new StringBuilder();
        if (!nickname.isBlank()) {
            sb.append("昵称: ").append(nickname).append('\n');
        }
        if (!username.isBlank()) {
            sb.append("用户名: ").append(username).append('\n');
        }
        if (!remark.isBlank()) {
            sb.append("简介: ").append(remark);
        }
        return sb.toString().trim();
    }

    /** 普通搜索：用户名或昵称包含关键词 */
    private LambdaQueryWrapper<User> buildDbFuzzyUserQuery(String kw) {
        List<String> terms = keywordTerms(kw);
        return new LambdaQueryWrapper<User>()
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN)
                .and(w -> applyTextLike(w, terms, User::getUsername, User::getNickname))
                .orderByDesc(User::getUpdateTime);
    }

    @SafeVarargs
    private <T> void applyTextLike(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> w,
            List<String> terms,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?>... columns) {
        boolean first = true;
        for (String term : terms) {
            for (var col : columns) {
                if (first) {
                    w.like(col, term);
                    first = false;
                } else {
                    w.or().like(col, term);
                }
            }
        }
    }

    /** 原词 + 分词 + 同义扩展（控制词数，减轻 MySQL / AI 压力） */
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
        return rows.stream().map(this::toListResponse).collect(Collectors.toList());
    }

    /** RAG 命中 ID 回表（仅展示，不参与检索）；不按 PUBLISHED 过滤，避免与向量库不同步导致空列表。 */
    private List<ArticleListResponse> buildListResponsesForRag(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN));
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
        return out;
    }

    private ArticleListResponse toListResponse(Article article) {
        ArticleListResponse vo = new ArticleListResponse();
        vo.setArticle(article);
        try {
            User u = userService.getUserInfoById(article.getUserId());
            if (u != null) vo.setUser(new UserBriefVO(u));
        } catch (ApplicationException e) {
            log.warn("搜索结果中作者 {} 已不可用, 跳过用户信息", article.getUserId());
        }
        return vo;
    }

    private PageResult<UserBriefVO> wrapUsers(Page<User> page, int p, int s, String kw) {
        List<String> terms = keywordTerms(kw);
        List<User> sorted = new ArrayList<>(page.getRecords());
        sorted.sort((a, b) -> Integer.compare(
                userLiteralScore(b, terms),
                userLiteralScore(a, terms)));
        List<UserBriefVO> records = sorted.stream()
                .map(UserBriefVO::new)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), p, s, page.getPages(), page.hasNext());
    }

    private static int userLiteralScore(User user, List<String> terms) {
        String nickname = user.getNickname() == null ? "" : user.getNickname();
        String username = user.getUsername() == null ? "" : user.getUsername();
        return SearchKeywordHelper.literalRelevanceScore(nickname, username, terms);
    }

    private PageResult<UserBriefVO> emptyUserPage(int p, int s) {
        return new PageResult<>(Collections.emptyList(), 0L, p, s, 0L, false);
    }

    private List<UserBriefVO> buildUserBriefListForRag(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<User> rows = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, ids)
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN));
        Map<Long, User> byId = new HashMap<>(rows.size() * 2);
        for (User u : rows) {
            byId.put(u.getId(), u);
        }
        List<UserBriefVO> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            User u = byId.get(id);
            if (u == null) {
                continue;
            }
            out.add(new UserBriefVO(u));
        }
        return out;
    }
}
