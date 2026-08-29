package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.enums.VideoTranscodeStatus;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleImage;
import org.pluchon.forum.mapper.ArticleImageMapper;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMediaService;
import org.pluchon.forum.service.interfaces.article.ArticlePublishSideEffectService;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicService;
import org.pluchon.forum.service.interfaces.article.ArticleVideoTranscodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private ArticleUserMusicService articleUserMusicService;

    @Autowired
    private ArticleVideoTranscodeService articleVideoTranscodeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId) {
        Article article = requireArticle(articleId);
        if (!article.getUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        validateArticleCoverUrl(coverUrl);
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setArticleVideo(Long articleId, Long loginUserId, String videoUrl) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请先上传视频"));
        }
        validateArticleVideoUrl(videoUrl);
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
                .set(Article::getVideoUrl, videoUrl.trim())
                .set(Article::getHlsUrl, null)
                .set(Article::getVideoTranscodeStatus, VideoTranscodeStatus.PROCESSING.getCode());
        if (wasPublished) {
            uw.set(Article::getStatus, ArticleStatus.DRAFT.getCode());
        }
        if (articleMapper.update(null, uw) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (wasPublished) {
            articlePublishSideEffectService.rollbackPublishedExposure(articleId, article.getBoardId(), loginUserId);
        }
        articleVideoTranscodeService.scheduleTranscode(articleId, videoUrl.trim());
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
                .set(Article::getVideoUrl, null)
                .set(Article::getHlsUrl, null)
                .set(Article::getVideoTranscodeStatus, VideoTranscodeStatus.NONE.getCode());
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
    public void setArticleMusic(Long articleId, Long loginUserId,
                                String musicKey, String musicTitle,
                                String musicCoverUrl, String musicAudioUrl, String musicLrcUrl) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (musicKey == null || musicKey.isBlank()
                || musicTitle == null || musicTitle.isBlank()
                || musicAudioUrl == null || musicAudioUrl.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐信息不完整"));
        }
        String audio = musicAudioUrl.trim();
        if (!ossConfig.matchesPublicObjectUrl(audio, Constant.OSS_PATH_MUSIC_INFO)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐音频无效，请重新选择"));
        }
        String cover = musicCoverUrl == null || musicCoverUrl.isBlank() ? null : musicCoverUrl.trim();
        if (cover != null && !ossConfig.matchesPublicObjectUrl(cover, Constant.OSS_PATH_MUSIC_AVATAR)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐封面无效，请重新选择"));
        }
        String lrc = musicLrcUrl == null || musicLrcUrl.isBlank() ? null : musicLrcUrl.trim();
        if (lrc != null && !ossConfig.matchesPublicObjectUrl(lrc, Constant.OSS_PATH_MUSIC_LRC)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐歌词无效，请重新选择"));
        }
        if (!articleUserMusicService.isBindable(musicKey.trim())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "该歌曲尚未过审，不能用于帖子"));
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
                .set(Article::getMusicKey, musicKey.trim())
                .set(Article::getMusicTitle, musicTitle.trim())
                .set(Article::getMusicCoverUrl, cover)
                .set(Article::getMusicAudioUrl, audio)
                .set(Article::getMusicLrcUrl, lrc);
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
    public void clearArticleMusic(Long articleId, Long loginUserId) {
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
                .set(Article::getMusicKey, null)
                .set(Article::getMusicTitle, null)
                .set(Article::getMusicCoverUrl, null)
                .set(Article::getMusicAudioUrl, null)
                .set(Article::getMusicLrcUrl, null);
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

    @Override
    public Map<Long, Integer> countImagesByArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = articleIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ArticleImage> rows = articleImageMapper.selectList(new LambdaQueryWrapper<ArticleImage>()
                .select(ArticleImage::getArticleId)
                .in(ArticleImage::getArticleId, ids)
                .ne(ArticleImage::getDeleteState, 1));
        Map<Long, Integer> counts = new HashMap<>();
        for (ArticleImage row : rows) {
            if (row.getArticleId() == null) {
                continue;
            }
            counts.merge(row.getArticleId(), 1, Integer::sum);
        }
        return counts;
    }

    @Override
    public Map<Long, String> firstImageUrlByArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = articleIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ArticleImage> rows = articleImageMapper.selectList(new LambdaQueryWrapper<ArticleImage>()
                .select(ArticleImage::getArticleId, ArticleImage::getImageUrl, ArticleImage::getSort, ArticleImage::getId)
                .in(ArticleImage::getArticleId, ids)
                .ne(ArticleImage::getDeleteState, 1)
                .orderByAsc(ArticleImage::getSort)
                .orderByAsc(ArticleImage::getId));
        Map<Long, String> firstUrls = new HashMap<>();
        for (ArticleImage row : rows) {
            if (row.getArticleId() == null) {
                continue;
            }
            if (firstUrls.containsKey(row.getArticleId())) {
                continue;
            }
            String url = row.getImageUrl();
            if (url == null || url.isBlank()) {
                continue;
            }
            firstUrls.put(row.getArticleId(), url.trim());
        }
        return firstUrls;
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
    // 封面此前完全不校验来源，可以指向任意外部地址：内容不可控、防盗链失效，
    // 超过 cover_img varchar(255) 还会直接撞数据库约束。
    // 合法来源只有两处：用户经 /file/uploadCover 上传的封面，以及 AI 生成后回传本站 OSS 的图
    private void validateArticleCoverUrl(String url) {
        if (url == null || url.isBlank()) {
            // 允许留空表示清空封面
            return;
        }
        String trimmed = url.trim();
        if (trimmed.length() > 255) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
        if (ossConfig.matchesPublicObjectUrl(trimmed, Constant.OSS_PATH_COVER)
                || ossConfig.matchesPublicObjectUrl(trimmed, Constant.OSS_PATH_AI_GENERATION_ARTICLE)) {
            return;
        }
        log.warn("帖子封面 URL 非法: {}", trimmed);
        throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
    }

    // 视频比封面更需要校验来源：审核链会拿这个 URL 去下载分析，
    // 不限定来源等于给了一个让服务端主动请求任意地址的口子。
    // 另外 video_url 是 varchar(500)，超长会直接撞数据库约束
    private void validateArticleVideoUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.length() > 500
                || !ossConfig.matchesPublicObjectUrl(trimmed, Constant.OSS_PATH_ARTICLE_VIDEO)) {
            log.warn("帖子视频 URL 非法: {}", trimmed);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

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
