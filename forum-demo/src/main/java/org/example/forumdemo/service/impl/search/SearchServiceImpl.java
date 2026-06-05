package org.example.forumdemo.service.impl.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
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
import org.example.forumdemo.service.interfaces.search.SearchService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AiHubService aiHubService;

    @Override
    public SearchArticleResponse searchArticles(String keyword, Integer pageNum, Integer pageSize, boolean preferAiRag) {
        if (!StringUtils.hasText(keyword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEARCH_KEYWORD_EMPTY));
        }
        String kw = keyword.trim();
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);

        if (!preferAiRag) {
            Page<Article> page = PageUtils.getPage(p, s);
            Page<Article> dbResult = articleMapper.selectPage(page, buildDbFuzzyArticleQuery(kw));
            if (dbResult.getRecords() != null && !dbResult.getRecords().isEmpty()) {
                return new SearchArticleResponse(Constant.SEARCH_SOURCE_DB, kw, wrap(dbResult, p, s));
            }
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }

        return searchArticlesByAiRag(kw, p, s);
    }

    /**
     * AI 语义搜索：仅 Redis RAG 向量库召回，不做 MySQL LIKE / 候选集 rerank。
     * 命中 ID 后仅按主键回表组装列表（展示用），不参与检索排序。
     */
    private SearchArticleResponse searchArticlesByAiRag(String kw, int p, int s) {
        List<Long> rankedIds = aiHubService.ragVectorSearchArticles(kw, Collections.emptyList());
        if (rankedIds.isEmpty()) {
            log.info("AI 帖子语义搜索未命中(RAG 向量库) keyword={}", kw);
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
                return new SearchUserResponse(Constant.SEARCH_SOURCE_DB, kw, wrapUsers(dbResult, p, s));
            }
            return new SearchUserResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyUserPage(p, s));
        }

        return searchUsersByAiRag(kw, p, s);
    }

    /** AI 用户搜索：仅 Redis 用户向量库，不走 MySQL 模糊匹配。 */
    private SearchUserResponse searchUsersByAiRag(String kw, int p, int s) {
        List<Long> rankedIds = aiHubService.ragVectorSearchUsers(kw, Collections.emptyList());
        if (rankedIds.isEmpty()) {
            log.info("AI 用户语义搜索未命中(RAG 向量库) keyword={}", kw);
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

    // ============ 内部工具 ============
    private PageResult<ArticleListResponse> wrap(Page<Article> page, int p, int s) {
        List<ArticleListResponse> records = page.getRecords().stream()
                .map(this::toListResponse).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), p, s, page.getPages(), page.hasNext());
    }

    private PageResult<ArticleListResponse> emptyPage(int p, int s) {
        return new PageResult<>(Collections.emptyList(), 0L, p, s, 0L, false);
    }

    /** 普通搜索：标题或正文包含关键词（模糊 LIKE） */
    private LambdaQueryWrapper<Article> buildDbFuzzyArticleQuery(String kw) {
        List<String> terms = keywordTerms(kw);
        return new QueryWrapper<Article>().lambda()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> applyTextLike(w, terms, Article::getTitle, Article::getContent))
                .orderByDesc(Article::getUpdateTime);
    }

    /** 普通搜索：用户名或昵称包含关键词 */
    private LambdaQueryWrapper<User> buildDbFuzzyUserQuery(String kw) {
        List<String> terms = keywordTerms(kw);
        return new QueryWrapper<User>().lambda()
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

    /** 原词 + 分词（普通 LIKE 搜索用） */
    private List<String> keywordTerms(String kw) {
        Set<String> set = new LinkedHashSet<>();
        if (StringUtils.hasText(kw)) {
            set.add(kw.trim());
            for (String t : tokenizeKeyword(kw)) {
                set.add(t);
            }
        }
        return new ArrayList<>(set);
    }

    private List<String> tokenizeKeyword(String kw) {
        if (!StringUtils.hasText(kw)) {
            return Collections.emptyList();
        }
        String[] parts = kw.trim().split("[\\s,，、；;|/\\\\]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.length() < 2) {
                continue;
            }
            if (!out.contains(t)) {
                out.add(t);
            }
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    /** RAG 命中 ID 回表（仅展示，不参与检索）；不按 PUBLISHED 过滤，避免与向量库不同步导致空列表。 */
    private List<ArticleListResponse> buildListResponsesForRag(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> rows = articleMapper.selectList(new QueryWrapper<Article>().lambda()
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

    private PageResult<UserBriefVO> wrapUsers(Page<User> page, int p, int s) {
        List<UserBriefVO> records = page.getRecords().stream()
                .map(UserBriefVO::new)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), p, s, page.getPages(), page.hasNext());
    }

    private PageResult<UserBriefVO> emptyUserPage(int p, int s) {
        return new PageResult<>(Collections.emptyList(), 0L, p, s, 0L, false);
    }

    private List<UserBriefVO> buildUserBriefListForRag(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<User> rows = userMapper.selectList(new QueryWrapper<User>().lambda()
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
