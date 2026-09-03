package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.common.constant.ForumTimeZone;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.ImageMagicValidator;
import org.pluchon.forum.common.utils.InMemoryMultipartFile;
import org.pluchon.forum.common.utils.OssFolderSupport;
import org.pluchon.forum.converter.UserMusicConverter;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.db.UserMusicFavorite;
import org.pluchon.forum.entity.db.UserMusicPlayHistory;
import org.pluchon.forum.entity.dto.article.ToggleMusicFavoriteRequest;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.UserMusicFavoriteMapper;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.mapper.UserMusicPlayHistoryMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicPlayStatService;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicAuditService;
import org.pluchon.forum.service.impl.file.AuditedOssImageUploader;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 用户歌曲上传落库、我的列表与收藏
@Slf4j
@Service
public class ArticleUserMusicServiceImpl implements ArticleUserMusicService {

    private static final byte DELETE_TRUE = 1;
    private static final byte DELETE_FALSE = 0;
    private static final int RECENT_PLAY_PAGE_SIZE = 5;
    // 与前端卡片的格子数对齐：收藏 2列×3行、上传/发布 2列×2行
    private static final int FAVORITE_PAGE_SIZE = 6;
    private static final int MINE_PAGE_SIZE = 4;
    // 同一首歌在这个窗口内重复上报只更新时间，不再累计播放量。
    // 前端已经要求播够 15 秒才上报，这里是挡直接打接口的兜底。
    private static final long PLAY_COUNT_WINDOW_MS = 60_000L;
    // 每人保留的播放历史条数，超出的按最后播放时间从旧到新裁掉
    private static final int PLAY_HISTORY_KEEP = 200;
    private static final DateTimeFormatter STEM_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private UserMusicFavoriteMapper userMusicFavoriteMapper;

    @Autowired
    private UserMusicPlayHistoryMapper userMusicPlayHistoryMapper;

    @Autowired
    private ArticleMusicPlayStatService articleMusicPlayStatService;

    @Autowired
    private ArticleMusicHotRankingService articleMusicHotRankingService;

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private OSS ossClient;

    @Autowired
    private AuditedOssImageUploader auditedOssImageUploader;

    @Autowired
    private ArticleUserMusicAuditService articleUserMusicAuditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MusicTrackVO upload(Long userId, String action, Long id, String title, String artist, String album,
                               String durationText, String lyricText, String moodTags,
                               MultipartFile audio, MultipartFile cover, MultipartFile lrc) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        boolean publish = "publish".equalsIgnoreCase(trimToEmpty(action));
        if (!publish && !"draft".equalsIgnoreCase(trimToEmpty(action))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "action 仅支持 draft 或 publish"));
        }
        String safeTitle = requireText(title, "请填写歌名", "歌名");
        String safeArtist = requireText(artist, "请填写歌手", "歌手");
        String safeAlbum = requireLength(trimToEmpty(album), Constant.MUSIC_TITLE_MAX_LEN, "专辑");
        String safeDuration = clip(trimToEmpty(durationText), 16);
        String mergedLyric = mergeLyric(lyricText, lrc);
        String moodTagsJson = encodeMoodTags(moodTags);

        UserMusic existing = null;
        if (id != null && id > 0) {
            existing = userMusicMapper.selectById(id);
            if (existing == null || !userId.equals(existing.getUserId())
                    || existing.getDeleteState() != null && existing.getDeleteState() == DELETE_TRUE) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌曲不存在"));
            }
            if (existing.getStatus() != null
                    && existing.getStatus() != Constant.USER_MUSIC_STATUS_DRAFT
                    && existing.getStatus() != Constant.USER_MUSIC_STATUS_REJECTED) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "审核中或已发布的歌曲不能再编辑"));
            }
        }

        boolean hasAudio = audio != null && !audio.isEmpty();
        if (existing == null && !hasAudio) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请先选择歌曲本体"));
        }
        // 只拦新建草稿：编辑已有草稿、以及直接发布都不受这个上限影响
        if (existing == null && !publish) {
            Long drafts = userMusicMapper.selectCount(new LambdaQueryWrapper<UserMusic>()
                    .eq(UserMusic::getUserId, userId)
                    .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_DRAFT)
                    .ne(UserMusic::getDeleteState, DELETE_TRUE));
            if (drafts != null && drafts >= Constant.MUSIC_DRAFT_MAX_COUNT) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                        "草稿最多保存 " + Constant.MUSIC_DRAFT_MAX_COUNT + " 份，请先发布或清理一些"));
            }
        }

        String musicKey;
        String audioUrl;
        String coverUrl;
        String lrcUrl;
        if (hasAudio) {
            musicKey = nextUniqueStem(userId, safeTitle);
            audioUrl = putAudio(userId, musicKey, audio);
            coverUrl = putCover(userId, musicKey, cover);
            lrcUrl = putLrc(userId, musicKey, lrc, mergedLyric);
        } else {
            musicKey = existing.getMusicKey();
            audioUrl = existing.getAudioUrl();
            coverUrl = existing.getCoverUrl();
            lrcUrl = existing.getLrcUrl();
            if (cover != null && !cover.isEmpty()) {
                coverUrl = putCover(userId, musicKey, cover);
            }
            if ((lrc != null && !lrc.isEmpty()) || StringUtils.hasText(mergedLyric)) {
                lrcUrl = putLrc(userId, musicKey, lrc, mergedLyric);
            }
        }

        byte status = publish ? Constant.USER_MUSIC_STATUS_REVIEWING : Constant.USER_MUSIC_STATUS_DRAFT;
        Date now = new Date();
        if (existing == null) {
            UserMusic row = new UserMusic();
            row.setUserId(userId);
            row.setMusicKey(musicKey);
            row.setTitle(safeTitle);
            row.setArtist(safeArtist);
            row.setAlbum(blankToNull(safeAlbum));
            row.setDurationText(blankToNull(safeDuration));
            row.setCoverUrl(coverUrl);
            row.setAudioUrl(audioUrl);
            row.setLrcUrl(lrcUrl);
            row.setLyricText(blankToNull(mergedLyric));
            row.setMoodTags(moodTagsJson);
            row.setStatus(status);
            row.setDeleteState(DELETE_FALSE);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userMusicMapper.insert(row);
            if (publish) {
                articleUserMusicAuditService.scheduleAudit(row.getId());
            }
            return UserMusicConverter.toTrackVO(row, true);
        }

        LambdaUpdateWrapper<UserMusic> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserMusic::getId, existing.getId())
                .eq(UserMusic::getUserId, userId)
                .ne(UserMusic::getDeleteState, DELETE_TRUE)
                .set(UserMusic::getMusicKey, musicKey)
                .set(UserMusic::getTitle, safeTitle)
                .set(UserMusic::getArtist, safeArtist)
                .set(UserMusic::getAlbum, blankToNull(safeAlbum))
                .set(UserMusic::getDurationText, blankToNull(safeDuration))
                .set(UserMusic::getCoverUrl, coverUrl)
                .set(UserMusic::getAudioUrl, audioUrl)
                .set(UserMusic::getLrcUrl, lrcUrl)
                .set(UserMusic::getLyricText, blankToNull(mergedLyric))
                .set(UserMusic::getMoodTags, moodTagsJson)
                .set(UserMusic::getStatus, status)
                .set(UserMusic::getUpdateTime, now);
        if (publish) {
            uw.set(UserMusic::getReviewResult, null);
            uw.set(UserMusic::getAiProfile, null);
            uw.set(UserMusic::getAiAnalyzedAt, null);
        }
        userMusicMapper.update(null, uw);
        UserMusic saved = userMusicMapper.selectById(existing.getId());
        if (publish) {
            articleUserMusicAuditService.scheduleAudit(saved.getId());
        }
        return UserMusicConverter.toTrackVO(saved, true);
    }

    @Override
    public PageResult<MusicTrackVO> pageMine(Long userId, String scope, String status, String keyword,
                                             Integer pageNum, Integer pageSize) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        String s = trimToEmpty(scope).toLowerCase(Locale.ROOT);
        boolean publishScope = "publish".equals(s);
        LambdaQueryWrapper<UserMusic> qw = new LambdaQueryWrapper<>();
        qw.eq(UserMusic::getUserId, userId)
                .ne(UserMusic::getDeleteState, DELETE_TRUE);
        if (publishScope) {
            qw.eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED);
        } else {
            Byte wanted = statusCodeToDb(status);
            if (wanted != null) {
                qw.eq(UserMusic::getStatus, wanted);
            } else {
                // 「全部」含已发布：已发布本来就是一种状态，
                // 而且下架/删除这两个管理动作就挂在已发布的卡片上
                qw.in(UserMusic::getStatus,
                        Constant.USER_MUSIC_STATUS_DRAFT,
                        Constant.USER_MUSIC_STATUS_REVIEWING,
                        Constant.USER_MUSIC_STATUS_REJECTED,
                        Constant.USER_MUSIC_STATUS_PUBLISHED);
            }
        }
        String kw = trimToEmpty(keyword);
        if (StringUtils.hasText(kw)) {
            qw.and(w -> w.like(UserMusic::getTitle, kw).or().like(UserMusic::getArtist, kw));
        }
        qw.orderByDesc(UserMusic::getUpdateTime).orderByDesc(UserMusic::getId);

        Page<UserMusic> page = new Page<>(validPageNum(pageNum), validPageSize(pageSize, MINE_PAGE_SIZE));
        Page<UserMusic> result = clampPage(userMusicMapper, page, qw);
        List<MusicTrackVO> records = new ArrayList<>(result.getRecords().size());
        for (UserMusic row : result.getRecords()) {
            records.add(UserMusicConverter.toTrackVO(row, true));
        }
        markFavorited(userId, records);
        return toPageResult(records, result);
    }

    // 前端传的是 statusCode 那套字符串，这里翻回库里的 tinyint
    private static Byte statusCodeToDb(String status) {
        String v = trimToEmpty(status).toLowerCase(Locale.ROOT);
        return switch (v) {
            case "draft" -> Constant.USER_MUSIC_STATUS_DRAFT;
            case "reviewing" -> Constant.USER_MUSIC_STATUS_REVIEWING;
            case "rejected" -> Constant.USER_MUSIC_STATUS_REJECTED;
            case "published" -> Constant.USER_MUSIC_STATUS_PUBLISHED;
            default -> null;
        };
    }

    @Override
    public PageResult<MusicTrackVO> pageFavorites(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        Page<UserMusicFavorite> page = new Page<>(validPageNum(pageNum),
                validPageSize(pageSize, FAVORITE_PAGE_SIZE));
        LambdaQueryWrapper<UserMusicFavorite> favQw = new LambdaQueryWrapper<UserMusicFavorite>()
                .eq(UserMusicFavorite::getUserId, userId)
                .ne(UserMusicFavorite::getDeleteState, DELETE_TRUE)
                // 取消收藏与重新收藏都会刷新 update_time，对可见行来说它就是收藏时间
                .orderByDesc(UserMusicFavorite::getUpdateTime)
                .orderByDesc(UserMusicFavorite::getId);
        Page<UserMusicFavorite> result = clampPage(userMusicFavoriteMapper, page, favQw);
        List<MusicTrackVO> records = new ArrayList<>(result.getRecords().size());
        for (UserMusicFavorite row : result.getRecords()) {
            records.add(UserMusicConverter.toTrackVO(row));
        }
        return toPageResult(records, result);
    }

    private static int validPageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private static int validPageSize(Integer pageSize, int fallback) {
        int size = pageSize == null || pageSize < 1 ? fallback : pageSize;
        return Math.min(size, 50);
    }

    // 取消收藏 / 删草稿之后总页数会变少，停在越界页上会拿到空列表，
    // 这里统一把页码收回最后一页再查一次
    private static <T> Page<T> clampPage(BaseMapper<T> mapper, Page<T> page,
                                         com.baomidou.mybatisplus.core.conditions.Wrapper<T> wrapper) {
        Page<T> result = mapper.selectPage(page, wrapper);
        if (result.getCurrent() > result.getPages() && result.getPages() >= 1) {
            Page<T> retry = new Page<>(result.getPages(), result.getSize());
            return mapper.selectPage(retry, wrapper);
        }
        return result;
    }

    private static <T> PageResult<T> toPageResult(List<T> records, Page<?> page) {
        return new PageResult<>(records, page.getTotal(), (int) page.getCurrent(),
                (int) page.getSize(), page.getPages(), page.getCurrent() < page.getPages());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long userId, ToggleMusicFavoriteRequest req) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        if (req == null || !StringUtils.hasText(req.getMusicKey()) || !StringUtils.hasText(req.getAudioUrl())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "收藏信息不完整"));
        }
        String musicKey = req.getMusicKey().trim();
        String audio = req.getAudioUrl().trim();
        if (!ossConfig.matchesPublicObjectUrl(audio, Constant.OSS_PATH_MUSIC_INFO)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐音频地址非法"));
        }
        String cover = blankToNull(trimToEmpty(req.getCoverUrl()));
        if (cover != null && !ossConfig.matchesPublicObjectUrl(cover, Constant.OSS_PATH_MUSIC_AVATAR)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐封面地址非法"));
        }
        String lrc = blankToNull(trimToEmpty(req.getLrcUrl()));
        if (lrc != null && !ossConfig.matchesPublicObjectUrl(lrc, Constant.OSS_PATH_MUSIC_LRC)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐歌词地址非法"));
        }
        UserMusicFavorite existed = userMusicFavoriteMapper.selectOne(new LambdaQueryWrapper<UserMusicFavorite>()
                .eq(UserMusicFavorite::getUserId, userId)
                .eq(UserMusicFavorite::getMusicKey, musicKey)
                .last("LIMIT 1"));
        Date now = new Date();
        if (existed != null && (existed.getDeleteState() == null || existed.getDeleteState() != DELETE_TRUE)) {
            userMusicFavoriteMapper.update(null, new LambdaUpdateWrapper<UserMusicFavorite>()
                    .eq(UserMusicFavorite::getId, existed.getId())
                    .set(UserMusicFavorite::getDeleteState, DELETE_TRUE)
                    .set(UserMusicFavorite::getUpdateTime, now));
            refreshHotScoreQuietly(musicKey);
            return false;
        }
        String title = StringUtils.hasText(req.getTitle()) ? clip(req.getTitle().trim(), Constant.MUSIC_TITLE_MAX_LEN) : musicKey;
        if (existed == null) {
            UserMusicFavorite row = new UserMusicFavorite();
            row.setUserId(userId);
            row.setMusicKey(musicKey);
            row.setTitle(title);
            row.setArtist(blankToNull(clip(trimToEmpty(req.getArtist()), Constant.MUSIC_TITLE_MAX_LEN)));
            row.setAlbum(blankToNull(clip(trimToEmpty(req.getAlbum()), Constant.MUSIC_TITLE_MAX_LEN)));
            row.setDurationText(blankToNull(clip(trimToEmpty(req.getDurationText()), 16)));
            row.setCoverUrl(cover);
            row.setAudioUrl(audio);
            row.setLrcUrl(lrc);
            row.setDeleteState(DELETE_FALSE);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userMusicFavoriteMapper.insert(row);
            refreshHotScoreQuietly(musicKey);
            return true;
        }
        userMusicFavoriteMapper.update(null, new LambdaUpdateWrapper<UserMusicFavorite>()
                .eq(UserMusicFavorite::getId, existed.getId())
                .set(UserMusicFavorite::getTitle, title)
                .set(UserMusicFavorite::getArtist, blankToNull(clip(trimToEmpty(req.getArtist()), Constant.MUSIC_TITLE_MAX_LEN)))
                .set(UserMusicFavorite::getAlbum, blankToNull(clip(trimToEmpty(req.getAlbum()), Constant.MUSIC_TITLE_MAX_LEN)))
                .set(UserMusicFavorite::getDurationText, blankToNull(clip(trimToEmpty(req.getDurationText()), 16)))
                .set(UserMusicFavorite::getCoverUrl, cover)
                .set(UserMusicFavorite::getAudioUrl, audio)
                .set(UserMusicFavorite::getLrcUrl, lrc)
                .set(UserMusicFavorite::getDeleteState, DELETE_FALSE)
                .set(UserMusicFavorite::getUpdateTime, now));
        refreshHotScoreQuietly(musicKey);
        return true;
    }

    @Override
    public PageResult<MusicTrackVO> pageRecentPlays(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        int size = pageSize == null || pageSize < 1 ? RECENT_PLAY_PAGE_SIZE : Math.min(pageSize, 20);
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        LambdaQueryWrapper<UserMusicPlayHistory> wrapper = new LambdaQueryWrapper<UserMusicPlayHistory>()
                .eq(UserMusicPlayHistory::getUserId, userId)
                .ne(UserMusicPlayHistory::getDeleteState, DELETE_TRUE)
                .orderByDesc(UserMusicPlayHistory::getUpdateTime);
        long total = userMusicPlayHistoryMapper.selectCount(wrapper);
        long pages = total == 0 ? 1 : (total + size - 1) / size;
        if (page > pages) {
            page = (int) pages;
        }
        Page<UserMusicPlayHistory> mpPage = new Page<>(page, size);
        List<UserMusicPlayHistory> rows = userMusicPlayHistoryMapper.selectPage(mpPage, wrapper).getRecords();
        List<MusicTrackVO> records = new ArrayList<>(rows.size());
        for (UserMusicPlayHistory row : rows) {
            records.add(UserMusicConverter.toTrackVO(row));
        }
        markFavorited(userId, records);
        return new PageResult<>(records, total, page, size, pages, (long) page < pages);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPlay(Long userId, ToggleMusicFavoriteRequest req) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        MusicTrackSnapshot snapshot = parseTrackSnapshot(req);
        UserMusicPlayHistory existed = userMusicPlayHistoryMapper.selectOne(new LambdaQueryWrapper<UserMusicPlayHistory>()
                .eq(UserMusicPlayHistory::getUserId, userId)
                .eq(UserMusicPlayHistory::getMusicKey, snapshot.musicKey())
                .last("LIMIT 1"));
        Date now = new Date();
        if (existed == null) {
            UserMusicPlayHistory row = new UserMusicPlayHistory();
            row.setUserId(userId);
            row.setMusicKey(snapshot.musicKey());
            row.setTitle(snapshot.title());
            row.setArtist(snapshot.artist());
            row.setAlbum(snapshot.album());
            row.setDurationText(snapshot.durationText());
            row.setCoverUrl(snapshot.coverUrl());
            row.setAudioUrl(snapshot.audioUrl());
            row.setLrcUrl(snapshot.lrcUrl());
            row.setDeleteState(DELETE_FALSE);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userMusicPlayHistoryMapper.insert(row);
            articleMusicPlayStatService.incrementPlayCount(snapshot.musicKey());
            trimPlayHistory(userId);
            return;
        }

        Date lastPlayed = existed.getUpdateTime();
        boolean countable = lastPlayed == null
                || now.getTime() - lastPlayed.getTime() >= PLAY_COUNT_WINDOW_MS;
        userMusicPlayHistoryMapper.update(null, new LambdaUpdateWrapper<UserMusicPlayHistory>()
                .eq(UserMusicPlayHistory::getId, existed.getId())
                .set(UserMusicPlayHistory::getTitle, snapshot.title())
                .set(UserMusicPlayHistory::getArtist, snapshot.artist())
                .set(UserMusicPlayHistory::getAlbum, snapshot.album())
                .set(UserMusicPlayHistory::getDurationText, snapshot.durationText())
                .set(UserMusicPlayHistory::getCoverUrl, snapshot.coverUrl())
                .set(UserMusicPlayHistory::getAudioUrl, snapshot.audioUrl())
                .set(UserMusicPlayHistory::getLrcUrl, snapshot.lrcUrl())
                .set(UserMusicPlayHistory::getDeleteState, DELETE_FALSE)
                .set(UserMusicPlayHistory::getUpdateTime, now));
        if (countable) {
            articleMusicPlayStatService.incrementPlayCount(snapshot.musicKey());
        }
    }

    // 播放历史没有天然上限，听得越多表越长；只保留最近 PLAY_HISTORY_KEEP 条
    private void trimPlayHistory(Long userId) {
        Long total = userMusicPlayHistoryMapper.selectCount(new LambdaQueryWrapper<UserMusicPlayHistory>()
                .eq(UserMusicPlayHistory::getUserId, userId)
                .ne(UserMusicPlayHistory::getDeleteState, DELETE_TRUE));
        if (total == null || total <= PLAY_HISTORY_KEEP) {
            return;
        }
        List<UserMusicPlayHistory> stale = userMusicPlayHistoryMapper.selectList(
                new LambdaQueryWrapper<UserMusicPlayHistory>()
                        .eq(UserMusicPlayHistory::getUserId, userId)
                        .ne(UserMusicPlayHistory::getDeleteState, DELETE_TRUE)
                        .orderByAsc(UserMusicPlayHistory::getUpdateTime)
                        .last("LIMIT " + (total - PLAY_HISTORY_KEEP)));
        for (UserMusicPlayHistory row : stale) {
            userMusicPlayHistoryMapper.update(null, new LambdaUpdateWrapper<UserMusicPlayHistory>()
                    .eq(UserMusicPlayHistory::getId, row.getId())
                    .set(UserMusicPlayHistory::getDeleteState, DELETE_TRUE));
        }
    }

    @Override
    public void markFavorited(Long userId, List<MusicTrackVO> tracks) {
        if (userId == null || tracks == null || tracks.isEmpty()) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (MusicTrackVO track : tracks) {
            if (track != null && StringUtils.hasText(track.getMusicKey())) {
                keys.add(track.getMusicKey());
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        List<UserMusicFavorite> rows = userMusicFavoriteMapper.selectList(new LambdaQueryWrapper<UserMusicFavorite>()
                .eq(UserMusicFavorite::getUserId, userId)
                .ne(UserMusicFavorite::getDeleteState, DELETE_TRUE)
                .in(UserMusicFavorite::getMusicKey, keys));
        Set<String> liked = new HashSet<>();
        for (UserMusicFavorite row : rows) {
            liked.add(row.getMusicKey());
        }
        for (MusicTrackVO track : tracks) {
            if (track != null) {
                track.setFavorited(liked.contains(track.getMusicKey()));
            }
        }
    }

    @Override
    public List<UserMusic> listPublishedWithAiProfile() {
        return userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .isNotNull(UserMusic::getAiProfile)
                .ne(UserMusic::getAiProfile, "")
                .ne(UserMusic::getDeleteState, DELETE_TRUE)
                .orderByDesc(UserMusic::getUpdateTime));
    }

    @Override
    public boolean isBindable(String musicKey) {
        if (!StringUtils.hasText(musicKey)) {
            return false;
        }
        UserMusic row = userMusicMapper.selectOne(new LambdaQueryWrapper<UserMusic>()
                .eq(UserMusic::getMusicKey, musicKey.trim())
                .last("LIMIT 1"));
        if (row == null) {
            return true;
        }
        if (row.getDeleteState() != null && row.getDeleteState() == DELETE_TRUE) {
            return false;
        }
        return row.getStatus() != null && row.getStatus() == Constant.USER_MUSIC_STATUS_PUBLISHED;
    }

    // 取出自己名下未删除的歌，取不到一律按「不存在」——不区分是没有还是不属于你
    private UserMusic requireOwnMusic(Long userId, Long id) {
        if (userId == null || id == null || id <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "参数无效"));
        }
        UserMusic row = userMusicMapper.selectById(id);
        if (row == null || !userId.equals(row.getUserId())
                || row.getDeleteState() != null && row.getDeleteState() == DELETE_TRUE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineOwnMusic(Long userId, Long id) {
        UserMusic row = requireOwnMusic(userId, id);
        if (!Byte.valueOf(Constant.USER_MUSIC_STATUS_PUBLISHED).equals(row.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "只有已发布的歌曲才能下架"));
        }
        // 回到「未发布」而不是新增一种状态：改完还能再投一次审。
        // OSS 一个字节都不动——收藏过这首歌的人靠快照继续听
        int updated = userMusicMapper.update(null, new LambdaUpdateWrapper<UserMusic>()
                .eq(UserMusic::getId, row.getId())
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .set(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_DRAFT)
                .set(UserMusic::getUpdateTime, new Date()));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnMusic(Long userId, Long id) {
        UserMusic row = requireOwnMusic(userId, id);
        int updated = userMusicMapper.update(null, new LambdaUpdateWrapper<UserMusic>()
                .eq(UserMusic::getId, row.getId())
                .ne(UserMusic::getDeleteState, DELETE_TRUE)
                .set(UserMusic::getDeleteState, DELETE_TRUE)
                .set(UserMusic::getUpdateTime, new Date()));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MusicTrackVO retryAudit(Long userId, Long id) {
        if (userId == null || id == null || id <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "参数无效"));
        }
        UserMusic row = userMusicMapper.selectById(id);
        if (row == null || !userId.equals(row.getUserId())
                || row.getDeleteState() != null && row.getDeleteState() == DELETE_TRUE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌曲不存在"));
        }
        MusicTrackVO preview = UserMusicConverter.toTrackVO(row, false);
        if (!"service_error".equals(preview.getReviewKind())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "当前歌曲不支持重新审核"));
        }
        Date now = new Date();
        userMusicMapper.update(null, new LambdaUpdateWrapper<UserMusic>()
                .eq(UserMusic::getId, row.getId())
                .set(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_REVIEWING)
                .set(UserMusic::getReviewResult, null)
                .set(UserMusic::getUpdateTime, now));
        articleUserMusicAuditService.scheduleAudit(row.getId());
        UserMusic saved = userMusicMapper.selectById(row.getId());
        MusicTrackVO vo = UserMusicConverter.toTrackVO(saved, true);
        markFavorited(userId, List.of(vo));
        return vo;
    }

    private String nextUniqueStem(Long userId, String title) {
        ZonedDateTime now = ZonedDateTime.now(ForumTimeZone.ZONE_ID);
        for (int i = 0; i < 5; i++) {
            String stem = sanitizeTitlePart(title) + "_" + userId + "_" + now.plusSeconds(i).format(STEM_TIME);
            Long count = userMusicMapper.selectCount(new LambdaQueryWrapper<UserMusic>()
                    .eq(UserMusic::getMusicKey, stem));
            if (count == null || count == 0) {
                return stem;
            }
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED, "生成歌曲文件名失败，请稍后重试"));
    }

    // 前端传的是 JSON 数组，旧实现见到 [ 就只做长度截断直接入库，
    // 于是数量上限和单标签长度全被绕过，截断后还可能存进一段非法 JSON。
    // 现在两条路径都先解析成数组再统一清洗限量。
    private String encodeMoodTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        List<String> parsed;
        if (trimmed.startsWith("[")) {
            try {
                parsed = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {
                });
            } catch (Exception exception) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "标签格式无效"));
            }
        } else {
            parsed = new ArrayList<>(List.of(trimmed.split("[,，]")));
        }
        int rawCount = parsed == null ? 0 : (int) parsed.stream().filter(StringUtils::hasText).count();
        if (rawCount > Constant.MUSIC_MOOD_TAG_MAX_COUNT) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "最多选择 " + Constant.MUSIC_MOOD_TAG_MAX_COUNT + " 个标签"));
        }
        List<String> tags = MusicMoodTagServiceImpl.sanitizeTagList(parsed);
        if (tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception exception) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "标签格式无效"));
        }
    }

    private String putAudio(Long userId, String stem, MultipartFile audio) {
        validateAudio(audio);
        String ext = extOf(audio.getOriginalFilename(), "mp3");
        if (!Constant.MUSIC_AUDIO_EXT.contains(ext)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅支持 mp3 / wav / flac / m4a"));
        }
        return putObject(userId, Constant.OSS_PATH_MUSIC_INFO, stem + "." + ext, audio, audio.getContentType());
    }

    private String putCover(Long userId, String stem, MultipartFile cover) {
        if (cover == null || cover.isEmpty()) {
            return null;
        }
        validateCover(cover);
        String ext = extOf(cover.getOriginalFilename(), "jpg");
        if ("jpeg".equals(ext)) {
            ext = "jpg";
        }
        if (!Set.of("jpg", "png", "gif").contains(ext)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED));
        }
        String fileName = stem + "." + ext;
        // 封面只做 OSS 落盘，内容审核交给后续歌曲 AI 异步任务，避免提交接口被审图拖住
        return putObject(userId, Constant.OSS_PATH_MUSIC_AVATAR, fileName, cover,
                cover.getContentType() == null ? "image/jpeg" : cover.getContentType());
    }

    private String putLrc(Long userId, String stem, MultipartFile lrc, String lyricText) {
        MultipartFile file = lrc;
        if (file == null || file.isEmpty()) {
            if (!StringUtils.hasText(lyricText)) {
                return null;
            }
            byte[] bytes = lyricText.getBytes(StandardCharsets.UTF_8);
            file = new InMemoryMultipartFile("lrc", stem + ".lrc", "text/plain;charset=UTF-8", bytes);
        } else {
            if (file.getSize() > Constant.MUSIC_LRC_MAX_SIZE) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌词文件不能超过 1MB"));
            }
            String ext = extOf(file.getOriginalFilename(), "lrc");
            if (!Set.of("lrc", "txt").contains(ext)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌词仅支持 lrc / txt"));
            }
        }
        return putObject(userId, Constant.OSS_PATH_MUSIC_LRC, stem + ".lrc", file,
                file.getContentType() == null ? "text/plain" : file.getContentType());
    }

    private String putObject(Long userId, String folder, String fileName, MultipartFile file, String contentType) {
        ensureOssReady();
        String objectName = ossConfig.objectKey(folder, fileName);
        OSS ossClient = this.ossClient;
        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType == null ? "application/octet-stream" : contentType);
            metadata.setContentLength(file.getSize());
            OssFolderSupport.ensureFolderExists(ossClient, ossConfig, folder);
            ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream, metadata);
            log.info("OSS 歌曲文件上传成功, userId={}, key={}, size={}KB", userId, objectName, file.getSize() / 1024);
            return toPublicUrl(objectName);
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("OSS 歌曲文件上传失败, userId={}, key={}", userId, objectName, e);
            throw new ApplicationException("文件上传 OSS 失败: " + e.getMessage());
        } finally {

        }
    }

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请先选择歌曲本体"));
        }
        if (file.getSize() > Constant.MUSIC_AUDIO_MAX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌曲不能超过 50MB"));
        }
        String contentType = file.getContentType();
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains(";")) {
                lower = lower.substring(0, lower.indexOf(';')).trim();
            }
            if (!Constant.MUSIC_AUDIO_TYPES.contains(lower) && !lower.startsWith("audio/")) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅支持音频文件"));
            }
        }
    }

    private void validateCover(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !Constant.IMAGE_SUPPORTED_TYPES.contains(contentType)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED));
        }
        if (file.getSize() > Constant.IMAGE_HARD_MAX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "封面不能超过 " + (Constant.IMAGE_HARD_MAX_SIZE / 1024 / 1024) + "MB"));
        }
        ImageMagicValidator.validateSupportedImage(file);
    }

    private void ensureOssReady() {
        if (!ossConfig.isBucketConfigured()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "OSS 未配置，无法上传歌曲"));
        }
    }

    private String toPublicUrl(String objectKey) {
        String prefix = ossConfig.getUrlPrefix() == null ? "" : ossConfig.getUrlPrefix().trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + objectKey;
    }

    private static String mergeLyric(String lyricText, MultipartFile lrc) {
        String fromForm = trimToEmpty(lyricText);
        if (fromForm.length() > Constant.MUSIC_LYRIC_TEXT_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌词过长"));
        }
        if (StringUtils.hasText(fromForm)) {
            return fromForm;
        }
        if (lrc == null || lrc.isEmpty()) {
            return "";
        }
        try {
            String text = new String(lrc.getBytes(), StandardCharsets.UTF_8);
            if (text.length() > Constant.MUSIC_LYRIC_TEXT_MAX_LEN) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌词过长"));
            }
            return text;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "无法读取歌词文件"));
        }
    }

    private static String sanitizeTitlePart(String title) {
        String t = title.replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("[\\r\\n]", "").trim();
        t = t.replaceAll("\\s+", "_");
        if (t.isEmpty()) {
            t = "未命名";
        }
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    private static String requireText(String value, String message, String label) {
        String t = trimToEmpty(value);
        if (!StringUtils.hasText(t)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, message));
        }
        return requireLength(t, Constant.MUSIC_TITLE_MAX_LEN, label);
    }

    // 超长直接报错。静默截断会让用户以为存成功了，回头发现名字被砍了一半
    private static String requireLength(String value, int max, String label) {
        String t = trimToEmpty(value);
        if (t.length() > max) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    label + "不能超过 " + max + " 个字"));
        }
        return t;
    }

    private static String extOf(String filename, String fallback) {
        if (filename == null || !filename.contains(".")) {
            return fallback;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.isEmpty() ? fallback : ext;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void refreshHotScoreQuietly(String musicKey) {
        try {
            articleMusicHotRankingService.refreshTrackScore(musicKey);
        } catch (Exception ignored) {
            // 热榜刷新失败不影响收藏主流程
        }
    }

    private MusicTrackSnapshot parseTrackSnapshot(ToggleMusicFavoriteRequest req) {
        if (req == null || !StringUtils.hasText(req.getMusicKey()) || !StringUtils.hasText(req.getAudioUrl())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "播放信息不完整"));
        }
        String musicKey = req.getMusicKey().trim();
        String audio = req.getAudioUrl().trim();
        if (!ossConfig.matchesPublicObjectUrl(audio, Constant.OSS_PATH_MUSIC_INFO)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐音频地址非法"));
        }
        String cover = blankToNull(trimToEmpty(req.getCoverUrl()));
        if (cover != null && !ossConfig.matchesPublicObjectUrl(cover, Constant.OSS_PATH_MUSIC_AVATAR)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐封面地址非法"));
        }
        String lrc = blankToNull(trimToEmpty(req.getLrcUrl()));
        if (lrc != null && !ossConfig.matchesPublicObjectUrl(lrc, Constant.OSS_PATH_MUSIC_LRC)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "配乐歌词地址非法"));
        }
        String title = StringUtils.hasText(req.getTitle()) ? clip(req.getTitle().trim(), Constant.MUSIC_TITLE_MAX_LEN) : musicKey;
        return new MusicTrackSnapshot(
                musicKey,
                title,
                blankToNull(clip(trimToEmpty(req.getArtist()), Constant.MUSIC_TITLE_MAX_LEN)),
                blankToNull(clip(trimToEmpty(req.getAlbum()), Constant.MUSIC_TITLE_MAX_LEN)),
                blankToNull(clip(trimToEmpty(req.getDurationText()), 16)),
                cover,
                audio,
                lrc);
    }

    private record MusicTrackSnapshot(String musicKey,
                                      String title,
                                      String artist,
                                      String album,
                                      String durationText,
                                      String coverUrl,
                                      String audioUrl,
                                      String lrcUrl) {
    }
}
