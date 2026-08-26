package org.pluchon.forum.service.impl.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.SearchRedisKeys;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.utils.SearchKeywordHelper;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.service.interfaces.article.ArticleTagService;
import org.pluchon.forum.service.interfaces.search.ArticleSearchIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleSearchIndexServiceImpl implements ArticleSearchIndexService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleTagService articleTagService;

    @Override
    public void syncPublishedArticle(Long articleId) {
        if (articleId == null) {
            return;
        }
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN));
        if (article == null || !ArticleStatus.isPublished(article.getStatus())) {
            removeArticle(articleId);
            return;
        }
        List<String> tagNames = articleTagService.tagNamesByArticleId(articleId);
        indexPublishedArticle(article, tagNames);
    }

    @Override
    public void indexPublishedArticle(Article article, List<String> tagNames) {
        if (article == null || article.getId() == null) {
            return;
        }
        if (!ArticleStatus.isPublished(article.getStatus())) {
            removeArticle(article.getId());
            return;
        }
        removeArticle(article.getId());
        List<String> terms = SearchKeywordHelper.buildIndexTerms(
                article.getTitle() + " " + stripHtml(article.getContent()), tagNames);
        if (terms.isEmpty()) {
            return;
        }
        String idStr = String.valueOf(article.getId());
        for (String term : terms) {
            stringRedisTemplate.opsForSet().add(SearchRedisKeys.inverted(term), idStr);
        }
        stringRedisTemplate.opsForSet().add(
                SearchRedisKeys.articleTerms(article.getId()),
                terms.toArray(new String[0]));
        Map<String, String> forward = new HashMap<>(4);
        forward.put("title", article.getTitle() == null ? "" : article.getTitle());
        forward.put("userId", String.valueOf(article.getUserId()));
        long updatedAt = article.getUpdateTime() != null ? article.getUpdateTime().getTime() : 0L;
        forward.put("updateTime", String.valueOf(updatedAt));
        stringRedisTemplate.opsForHash().putAll(SearchRedisKeys.forward(article.getId()), forward);
        stringRedisTemplate.opsForSet().add(SearchRedisKeys.indexedArticles(), idStr);
        log.debug("搜索倒排已更新 articleId={} terms={}", article.getId(), terms.size());
    }

    @Override
    public void removeArticle(Long articleId) {
        if (articleId == null) {
            return;
        }
        Set<String> terms = stringRedisTemplate.opsForSet().members(SearchRedisKeys.articleTerms(articleId));
        String idStr = String.valueOf(articleId);
        if (terms != null) {
            for (String term : terms) {
                if (StringUtils.hasText(term)) {
                    stringRedisTemplate.opsForSet().remove(SearchRedisKeys.inverted(term), idStr);
                }
            }
        }
        stringRedisTemplate.delete(SearchRedisKeys.articleTerms(articleId));
        stringRedisTemplate.delete(SearchRedisKeys.forward(articleId));
        stringRedisTemplate.opsForSet().remove(SearchRedisKeys.indexedArticles(), idStr);
    }

    @Override
    public List<Long> searchPublishedIds(String keyword, int limit) {
        List<String> queryTerms = SearchKeywordHelper.expandTerms(keyword);
        if (queryTerms.isEmpty() || limit <= 0) {
            return List.of();
        }
        Map<Long, Integer> hitCount = new LinkedHashMap<>();
        for (String rawTerm : queryTerms) {
            String term = SearchKeywordHelper.normalizeTerm(rawTerm);
            if (!StringUtils.hasText(term)) {
                continue;
            }
            Set<String> members = stringRedisTemplate.opsForSet().members(SearchRedisKeys.inverted(term));
            if (members == null || members.isEmpty()) {
                continue;
            }
            for (String member : members) {
                try {
                    long id = Long.parseLong(member);
                    hitCount.merge(id, 1, Integer::sum);
                } catch (NumberFormatException ignore) {
                    // 跳过
                }
            }
        }
        if (hitCount.isEmpty()) {
            return List.of();
        }
        return hitCount.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<Long, Integer> e) -> e.getValue()).reversed()
                        .thenComparing(e -> readForwardUpdateTime(e.getKey()), Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public int rebuildAllPublished() {
        List<Article> published = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .select(Article::getId, Article::getTitle, Article::getContent, Article::getUserId, Article::getStatus,
                        Article::getUpdateTime));
        Set<Long> alive = new HashSet<>();
        int count = 0;
        for (Article row : published) {
            if (row.getId() == null) {
                continue;
            }
            alive.add(row.getId());
            syncPublishedArticle(row.getId());
            count++;
        }
        Set<String> indexed = stringRedisTemplate.opsForSet().members(SearchRedisKeys.indexedArticles());
        if (indexed != null) {
            for (String sid : indexed) {
                try {
                    long id = Long.parseLong(sid);
                    if (!alive.contains(id)) {
                        removeArticle(id);
                    }
                } catch (NumberFormatException ignore) {
                    stringRedisTemplate.opsForSet().remove(SearchRedisKeys.indexedArticles(), sid);
                }
            }
        }
        log.info("搜索倒排全量重建完成 count={}", count);
        return count;
    }

    private long readForwardUpdateTime(long articleId) {
        Object raw = stringRedisTemplate.opsForHash().get(SearchRedisKeys.forward(articleId), "updateTime");
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private static String stripHtml(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
