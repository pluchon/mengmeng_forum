package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.OssConfig;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleImage;
import org.example.forumdemo.mapper.ArticleImageMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.interfaces.article.ArticleMediaService;
import org.example.forumdemo.service.interfaces.article.ArticlePublishSideEffectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 帖子封面、相册与视频媒体落库
@Service
@Slf4j
public class ArticleMediaServiceImpl implements ArticleMediaService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleImageMapper articleImageMapper;

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private ArticlePublishSideEffectService articlePublishSideEffectService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId) {
        Article article = requireArticle(articleId);
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, articleId).set(Article::getCoverImg, coverUrl);
        if (wasPublished) {
            uw.set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        }
        if (articleMapper.update(null, uw) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (wasPublished) {
            articlePublishSideEffectService.rollbackPublishedExposure(articleId, article.getBoardId(), loginUserId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceArticleImages(Long articleId, Long loginUserId, List<String> imageUrls) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE));
        }
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        if (article.getMediaType() != null && article.getMediaType() == 1) {
            if (imageUrls == null || imageUrls.isEmpty()) {
                return;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "视频帖不支持相册图"));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        List<String> urls = imageUrls == null ? Collections.emptyList() : new ArrayList<>(imageUrls);
        if (urls.size() > Constant.ARTICLE_GALLERY_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE_GALLERY_LIMIT));
        }
        if (!urls.isEmpty()) {
            String raw = article.getContent() == null ? "" : article.getContent();
            String plain = raw.replaceAll("<[^>]+>", "").trim();
            if (plain.length() < Constant.ARTICLE_GALLERY_MIN_CONTENT_LEN) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE_GALLERY_NEEDS_CONTENT));
            }
        }
        for (String url : urls) {
            validateArticleImageUrl(url);
        }
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getMediaType, (byte) 0)
                .set(Article::getVideoUrl, null));
        articleImageMapper.update(null, new LambdaUpdateWrapper<ArticleImage>()
                .eq(ArticleImage::getArticleId, articleId)
                .ne(ArticleImage::getDeleteState, 1)
                .set(ArticleImage::getDeleteState, DELETE_TRUE));
        int sort = 0;
        for (String url : urls) {
            ArticleImage row = new ArticleImage();
            row.setArticleId(articleId);
            row.setImageUrl(url.trim());
            row.setSort(sort++);
            articleImageMapper.insert(row);
        }
        if (wasPublished) {
            int reset = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                    .ne(Article::getDeleteState, DELETE_TRUE)
                    .set(Article::getStatus, ArticleStatus.DRAFT.getCode()));
            if (reset > 0) {
                articlePublishSideEffectService.rollbackPublishedExposure(articleId, article.getBoardId(), loginUserId);
            }
        }
        log.info("帖子相册替换完成: articleId={}, count={}, userId={}", articleId, urls.size(), loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setArticleVideo(Long articleId, Long loginUserId, String videoUrl) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "视频地址为空"));
        }
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE));
        }
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        articleImageMapper.update(null, new LambdaUpdateWrapper<ArticleImage>()
                .eq(ArticleImage::getArticleId, articleId)
                .ne(ArticleImage::getDeleteState, 1)
                .set(ArticleImage::getDeleteState, DELETE_TRUE));
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getMediaType, (byte) 1)
                .set(Article::getVideoUrl, videoUrl.trim());
        if (wasPublished) {
            uw.set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        }
        if (articleMapper.update(null, uw) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (wasPublished) {
            articlePublishSideEffectService.rollbackPublishedExposure(articleId, article.getBoardId(), loginUserId);
        }
        log.info("帖子视频已绑定: articleId={}, userId={}", articleId, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearArticleVideo(Long articleId, Long loginUserId) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE));
        }
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (ArticleStatus.isEditingLocked(article.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_EDIT_LOCKED));
        }
        boolean wasPublished = ArticleStatus.isPublished(article.getStatus());
        LambdaUpdateWrapper<Article> uw = new LambdaUpdateWrapper<>();
        uw.eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getMediaType, (byte) 0)
                .set(Article::getVideoUrl, null);
        if (wasPublished) {
            uw.set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        }
        if (articleMapper.update(null, uw) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (wasPublished) {
            articlePublishSideEffectService.rollbackPublishedExposure(articleId, article.getBoardId(), loginUserId);
        }
    }

    @Override
    public List<String> queryArticleImageUrls(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return Collections.emptyList();
        }
        List<ArticleImage> rows = articleImageMapper.selectList(new LambdaQueryWrapper<ArticleImage>()
                .eq(ArticleImage::getArticleId, articleId)
                .ne(ArticleImage::getDeleteState, 1)
                .orderByAsc(ArticleImage::getSort)
                .orderByAsc(ArticleImage::getId));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>(rows.size());
        for (ArticleImage r : rows) {
            urls.add(r.getImageUrl());
        }
        return urls;
    }

    private Article requireArticle(Long articleId) {
        Article info = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN));
        if (info == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return info;
    }

    // 校验相册图 URL 必须落在 OSS_PATH_ARTICLE_IMAGE 子目录
    private void validateArticleImageUrl(String url) {
        if (!ossConfig.matchesPublicObjectUrl(url, Constant.OSS_PATH_ARTICLE_IMAGE)) {
            log.warn("帖子相册 URL 非法: {}", url);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

    private boolean containsControlChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
