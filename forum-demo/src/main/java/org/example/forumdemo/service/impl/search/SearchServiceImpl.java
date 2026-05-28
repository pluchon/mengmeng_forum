package org.example.forumdemo.service.impl.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import org.example.forumdemo.service.interfaces.search.SearchService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;
    /** RAG 候选侧正文摘录上限（与 Python doc_truncate 对齐前先做结构化截取） */
    private static final int RAG_TEXT_TRUNCATE = 1200;
    private static final int RAG_EXCERPT_HEAD = 700;
    private static final int RAG_EXCERPT_TAIL = 400;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Override
    public SearchArticleResponse searchArticles(String keyword, Integer pageNum, Integer pageSize, boolean preferAiRag) {
        if (!StringUtils.hasText(keyword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEARCH_KEYWORD_EMPTY));
        }
        String kw = keyword.trim();
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);

        // ============ 第一路: DB 标题模糊匹配（AI 搜索模式跳过） ============
        if (!preferAiRag) {
            Page<Article> page = PageUtils.getPage(p, s);
            Page<Article> dbResult = articleMapper.selectPage(page, new QueryWrapper<Article>().lambda()
                    .ne(Article::getDeleteState, DELETE_TRUE)
                    .ne(Article::getState, STATE_FORBIDDEN)
                    .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                    .like(Article::getTitle, kw)
                    .orderByDesc(Article::getUpdateTime));
            if (dbResult.getRecords() != null && !dbResult.getRecords().isEmpty()) {
                return new SearchArticleResponse(Constant.SEARCH_SOURCE_DB, kw, wrap(dbResult, p, s));
            }
        }

        // ============ 第二路: AI 语义召回（先宽松召回，再由 RAG 排序；支持「四川」≈「川西」等） ============
        List<Article> candidates = articleMapper.selectList(buildArticleRagCandidateQuery(kw));
        if (candidates.isEmpty()) {
            candidates = articleMapper.selectList(new QueryWrapper<Article>().lambda()
                    .ne(Article::getDeleteState, DELETE_TRUE)
                    .ne(Article::getState, STATE_FORBIDDEN)
                    .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                    .orderByDesc(Article::getUpdateTime)
                    .last("LIMIT " + Constant.SEARCH_RAG_CANDIDATE_LIMIT));
        }
        if (candidates.isEmpty()) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }
        List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
        for (Article a : candidates) {
            Map<String, Object> item = new HashMap<>(2);
            item.put("articleId", a.getId());
            item.put("text", buildRagText(a));
            payload.add(item);
        }
        List<Long> rankedIds = AiAuditUtils.ragSearchArticles(kw, payload);
        if (rankedIds.isEmpty()) {
            return new SearchArticleResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyPage(p, s));
        }
        // 截掉超过上限的部分; rerank 已按相关性降序排好
        if (rankedIds.size() > Constant.SEARCH_RAG_MAX_RESULTS) {
            rankedIds = rankedIds.subList(0, Constant.SEARCH_RAG_MAX_RESULTS);
        }
        // 手工分页: rerank 给的就是全集排序, 直接按 (p, s) 切片
        long total = rankedIds.size();
        int fromIdx = (p - 1) * s;
        int toIdx = Math.min(fromIdx + s, rankedIds.size());
        List<Long> pageSlice = fromIdx >= rankedIds.size() ? Collections.emptyList()
                : rankedIds.subList(fromIdx, toIdx);
        List<ArticleListResponse> records = buildListResponses(pageSlice, candidates);
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
            Page<User> dbResult = userMapper.selectPage(page, new QueryWrapper<User>().lambda()
                    .ne(User::getDeleteState, DELETE_TRUE)
                    .ne(User::getState, STATE_FORBIDDEN)
                    .and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw))
                    .orderByDesc(User::getUpdateTime));
            if (dbResult.getRecords() != null && !dbResult.getRecords().isEmpty()) {
                return new SearchUserResponse(Constant.SEARCH_SOURCE_DB, kw, wrapUsers(dbResult, p, s));
            }
        }

        List<User> candidates = userMapper.selectList(buildUserRagCandidateQuery(kw));
        if (candidates.isEmpty()) {
            candidates = userMapper.selectList(new QueryWrapper<User>().lambda()
                    .ne(User::getDeleteState, DELETE_TRUE)
                    .ne(User::getState, STATE_FORBIDDEN)
                    .orderByDesc(User::getUpdateTime)
                    .last("LIMIT " + Constant.SEARCH_RAG_CANDIDATE_LIMIT));
        }
        if (candidates.isEmpty()) {
            return new SearchUserResponse(Constant.SEARCH_SOURCE_EMPTY, kw, emptyUserPage(p, s));
        }
        List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
        for (User u : candidates) {
            Map<String, Object> item = new HashMap<>(2);
            item.put("userId", u.getId());
            item.put("text", buildUserRagText(u));
            payload.add(item);
        }
        List<Long> rankedIds = AiAuditUtils.ragSearchUsers(kw, payload);
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
        List<UserBriefVO> records = buildUserBriefList(pageSlice, candidates);
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

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Article> buildArticleRagCandidateQuery(String kw) {
        List<String> tokens = tokenizeKeyword(kw);
        return new QueryWrapper<Article>().lambda()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> {
                    w.like(Article::getTitle, kw).or().like(Article::getContent, kw);
                    for (String t : tokens) {
                        w.or().like(Article::getTitle, t).or().like(Article::getContent, t);
                    }
                    if (kw.length() >= 2) {
                        w.or().apply("LOCATE({0}, title) > 0", kw.substring(0, 1));
                    }
                })
                .orderByDesc(Article::getUpdateTime)
                .last("LIMIT " + Constant.SEARCH_RAG_CANDIDATE_LIMIT);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User> buildUserRagCandidateQuery(String kw) {
        List<String> tokens = tokenizeKeyword(kw);
        return new QueryWrapper<User>().lambda()
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN)
                .and(w -> {
                    w.like(User::getUsername, kw).or().like(User::getNickname, kw).or().like(User::getRemark, kw);
                    for (String t : tokens) {
                        w.or().like(User::getUsername, t).or().like(User::getNickname, t).or().like(User::getRemark, t);
                    }
                    if (kw.length() >= 2) {
                        w.or().apply("LOCATE({0}, nickname) > 0", kw.substring(0, 1));
                    }
                })
                .orderByDesc(User::getUpdateTime)
                .last("LIMIT " + Constant.SEARCH_RAG_CANDIDATE_LIMIT);
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

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String buildExcerpt(String plain, int maxLen) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (plain.length() <= maxLen) {
            return plain;
        }
        if (maxLen <= RAG_EXCERPT_HEAD + RAG_EXCERPT_TAIL + 8) {
            return plain.substring(0, maxLen);
        }
        String head = plain.substring(0, RAG_EXCERPT_HEAD);
        String tail = plain.substring(plain.length() - RAG_EXCERPT_TAIL);
        return head + "\n…\n" + tail;
    }

    /** RAG 召回侧文本: 标题 + 头尾摘录 + 互动信号，供 Python 融合排序. */
    private String buildRagText(Article a) {
        String title = a.getTitle() == null ? "" : a.getTitle();
        String plain = stripHtml(a.getContent());
        String body = buildExcerpt(plain, RAG_TEXT_TRUNCATE);
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank()) {
            sb.append("标题: ").append(title).append('\n');
        }
        if (!body.isBlank()) {
            sb.append("正文:\n").append(body).append('\n');
        }
        if (a.getReplyCount() != null && a.getReplyCount() > 0) {
            sb.append("回复数: ").append(a.getReplyCount()).append('\n');
        }
        if (a.getLikeCount() != null && a.getLikeCount() > 0) {
            sb.append("点赞数: ").append(a.getLikeCount()).append('\n');
        }
        return sb.toString().trim();
    }

    /** 按候选缓存批量装配; 候选里没的 articleId 会再去 DB 拉一次, 兜底极少出现. */
    private List<ArticleListResponse> buildListResponses(List<Long> ids, List<Article> candidates) {
        if (ids.isEmpty()) return Collections.emptyList();
        Map<Long, Article> byId = new HashMap<>(candidates.size() * 2);
        for (Article a : candidates) byId.put(a.getId(), a);
        List<Long> missing = new ArrayList<>();
        for (Long id : ids) if (!byId.containsKey(id)) missing.add(id);
        if (!missing.isEmpty()) {
            List<Article> extra = articleMapper.selectList(new QueryWrapper<Article>().lambda()
                    .in(Article::getId, missing).ne(Article::getDeleteState, DELETE_TRUE)
                    .ne(Article::getState, STATE_FORBIDDEN)
                    .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
            for (Article a : extra) byId.put(a.getId(), a);
        }
        List<ArticleListResponse> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Article a = byId.get(id);
            if (a == null) continue;
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

    /** RAG 侧用户文本: 昵称 + 用户名 + 简介摘录 */
    private String buildUserRagText(User u) {
        String nick = u.getNickname() == null ? "" : u.getNickname();
        String name = u.getUsername() == null ? "" : u.getUsername();
        String remark = buildExcerpt(u.getRemark() == null ? "" : u.getRemark(), RAG_TEXT_TRUNCATE);
        StringBuilder sb = new StringBuilder();
        if (!nick.isBlank()) {
            sb.append("昵称: ").append(nick).append('\n');
        }
        if (!name.isBlank()) {
            sb.append("用户名: ").append(name).append('\n');
        }
        if (!remark.isBlank()) {
            sb.append("简介: ").append(remark);
        }
        return sb.toString().trim();
    }

    private List<UserBriefVO> buildUserBriefList(List<Long> ids, List<User> candidates) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, User> byId = new HashMap<>(candidates.size() * 2);
        for (User u : candidates) {
            byId.put(u.getId(), u);
        }
        List<Long> missing = new ArrayList<>();
        for (Long id : ids) {
            if (!byId.containsKey(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            List<User> extra = userMapper.selectList(new QueryWrapper<User>().lambda()
                    .in(User::getId, missing)
                    .ne(User::getDeleteState, DELETE_TRUE)
                    .ne(User::getState, STATE_FORBIDDEN));
            for (User u : extra) {
                byId.put(u.getId(), u);
            }
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
