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

import java.util.concurrent.ExecutorService;

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

    @Override
    public void scheduleTranscode(Long articleId, String sourceVideoUrl) {
        if (articleId == null || articleId <= 0 || !StringUtils.hasText(sourceVideoUrl)) {
            return;
        }
        String trimmed = sourceVideoUrl.trim();
        TransactionHooks.afterCommit(() -> videoTranscodeExecutor.execute(() -> {
            try {
                processTranscode(articleId, trimmed);
            } catch (Exception exception) {
                log.error("视频 HLS 转码异步失败 articleId={}", articleId, exception);
                markFailed(articleId);
            }
        }));
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
        }
    }

    private void markFailed(Long articleId) {
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getVideoTranscodeStatus, VideoTranscodeStatus.FAILED.getCode()));
    }
}
