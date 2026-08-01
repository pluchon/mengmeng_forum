package org.example.forumdemo.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleLike;
import org.example.forumdemo.mapper.ArticleLikeMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 定时校准帖子 like_count 与 article_like 实际行数，防止并发漂移长期累积。
 */
@Slf4j
@ConditionalOnProperty(name = "forum.domain", havingValue = "content")
@Component
public class CounterCalibrationTask {

    private static final String LOCK_KEY = "forum:task:counter_calibration:lock";

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 30 3 * * ?")
    public void calibrateArticleLikeCounts() {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofMinutes(30));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            List<ArticleLike> likeRows = articleLikeMapper.selectList(
                    Wrappers.lambdaQuery(ArticleLike.class).select(ArticleLike::getArticleId));
            Map<Long, Long> actual = likeRows.stream()
                    .collect(Collectors.groupingBy(ArticleLike::getArticleId, Collectors.counting()));
            List<Article> articles = articleMapper.selectList(
                    Wrappers.lambdaQuery(Article.class).select(Article::getId, Article::getLikeCount));
            int fixed = 0;
            for (Article article : articles) {
                long expected = actual.getOrDefault(article.getId(), 0L);
                int current = article.getLikeCount() == null ? 0 : article.getLikeCount();
                if (current != expected) {
                    articleMapper.update(null, Wrappers.lambdaUpdate(Article.class)
                            .eq(Article::getId, article.getId())
                            .set(Article::getLikeCount, (int) expected));
                    fixed++;
                }
            }
            if (fixed > 0) {
                log.info("帖子点赞数校准完成: 修正 {} 条", fixed);
            }
        } catch (Exception e) {
            log.error("帖子点赞数校准失败", e);
        } finally {
            stringRedisTemplate.delete(LOCK_KEY);
        }
    }
}
