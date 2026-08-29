package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.VideoTranscodeStatus;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.service.interfaces.article.ArticleVideoTranscodeService;
import org.pluchon.forum.service.interfaces.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.constant.Constant;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

// 帖子视频 HLS 异步转码
@Service
@Slf4j
public class ArticleVideoTranscodeServiceImpl implements ArticleVideoTranscodeService {

    private static final byte DELETE_TRUE = 1;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    @Qualifier("videoTranscodeExecutor")
    private ExecutorService videoTranscodeExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void scheduleTranscode(Long articleId, String sourceVideoUrl) {
        if (articleId == null || articleId <= 0 || !StringUtils.hasText(sourceVideoUrl)) {
            return;
        }
        String trimmed = sourceVideoUrl.trim();
        TransactionHooks.afterCommit(() -> enqueue(articleId, trimmed));
    }

    // 队列满时线程池会直接抛 RejectedExecutionException（AbortPolicy）。
    // 这里咽掉即可：帖子仍是 PROCESSING，兜底任务会把它捞回来
    private void enqueue(Long articleId, String sourceVideoUrl) {
        try {
            videoTranscodeExecutor.execute(() -> {
                try {
                    processTranscode(articleId, sourceVideoUrl);
                } catch (Exception exception) {
                    log.error("视频 HLS 转码异步失败 articleId={}", articleId, exception);
                    markFailed(articleId);
                }
            });
        } catch (RejectedExecutionException rejected) {
            log.warn("视频转码队列已满，转入兜底重试 articleId={}", articleId);
        }
    }

    @Override
    public int sweepStuckTranscodes() {
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusMinutes(Constant.VIDEO_TRANSCODE_STALE_MINUTES);
        List<Article> stuck = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getMediaType, (byte) 1)
                .eq(Article::getVideoTranscodeStatus, VideoTranscodeStatus.PROCESSING.getCode())
                .ne(Article::getDeleteState, DELETE_TRUE)
                .isNotNull(Article::getVideoUrl)
                .lt(Article::getUpdateTime, staleBefore)
                .orderByAsc(Article::getUpdateTime)
                .last("limit " + Constant.VIDEO_TRANSCODE_SWEEP_BATCH));
        int handled = 0;
        for (Article article : stuck) {
            if (!StringUtils.hasText(article.getVideoUrl())) {
                continue;
            }
            enqueue(article.getId(), article.getVideoUrl().trim());
            handled++;
        }
        return handled;
    }

    public void processTranscode(Long articleId, String sourceVideoUrl) {
        if (articleId == null || articleId <= 0 || !StringUtils.hasText(sourceVideoUrl)) {
            return;
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null || article.getDeleteState() != null && article.getDeleteState() == DELETE_TRUE) {
            return;
        }
        if (article.getMediaType() == null || article.getMediaType() != 1) {
            return;
        }
        String currentVideoUrl = article.getVideoUrl();
        if (!StringUtils.hasText(currentVideoUrl) || !currentVideoUrl.trim().equals(sourceVideoUrl.trim())) {
            log.info("跳过过期 HLS 转码任务 articleId={}", articleId);
            return;
        }
        // 兜底任务可能把一个仍在转的任务再次入队，这里用一把带 TTL 的锁挡住重复执行
        String lockKey = Constant.REDIS_KEY_VIDEO_TRANSCODE_LOCK + articleId;
        boolean locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Constant.REDIS_TTL_VIDEO_TRANSCODE_LOCK, TimeUnit.SECONDS));
        if (!locked) {
            log.info("该帖子转码正在进行中，跳过重复任务 articleId={}", articleId);
            return;
        }
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getVideoTranscodeStatus, VideoTranscodeStatus.PROCESSING.getCode())
                .set(Article::getHlsUrl, null));
        try {
            String hlsUrl = fileService.transcodeArticleVideoToHls(articleId, sourceVideoUrl.trim());
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .eq(Article::getVideoUrl, sourceVideoUrl.trim())
                    .ne(Article::getDeleteState, DELETE_TRUE)
                    .set(Article::getHlsUrl, hlsUrl)
                    .set(Article::getVideoTranscodeStatus, VideoTranscodeStatus.READY.getCode()));
            log.info("视频 HLS 转码完成 articleId={} hlsUrl={}", articleId, hlsUrl);
        } catch (Exception exception) {
            log.error("视频 HLS 转码失败 articleId={}", articleId, exception);
            markFailed(articleId);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void markFailed(Long articleId) {
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getVideoTranscodeStatus, VideoTranscodeStatus.FAILED.getCode()));
    }
}
