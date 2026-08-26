package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.converter.UserMusicConverter;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.db.UserMusicPlayStat;
import org.pluchon.forum.entity.vo.article.MusicHotTrackVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.mapper.UserMusicPlayStatMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicDiscoverService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendMixService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendSlateService;
import org.pluchon.forum.util.MusicPlayCountFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 音乐大厅发现页：今日精选、推荐、热榜（热榜读 Redis）
@Service
public class ArticleMusicDiscoverServiceImpl implements ArticleMusicDiscoverService {

    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 50;

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private UserMusicPlayStatMapper userMusicPlayStatMapper;

    @Autowired
    private ArticleMusicRecommendSlateService articleMusicRecommendSlateService;

    @Autowired
    private ArticleMusicRecommendMixService articleMusicRecommendMixService;

    @Autowired
    private ArticleMusicHotRankingService articleMusicHotRankingService;

    @Override
    public MusicTrackVO getFeatured() {
        List<String> keys = articleMusicHotRankingService.listHotMusicKeys(0, 1);
        if (keys.isEmpty()) {
            return null;
        }
        List<MusicTrackVO> tracks = loadTracksByKeys(keys);
        return tracks.isEmpty() ? null : tracks.get(0);
    }

    @Override
    public PageResult<MusicTrackVO> pageRecommend(Long userId, Integer pageNum, Integer pageSize) {
        List<MusicTrackVO> candidates;
        List<MusicTrackVO> slateTracks = articleMusicRecommendSlateService.loadActiveTracks(userId);
        if (slateTracks != null && !slateTracks.isEmpty()) {
            candidates = new ArrayList<>(slateTracks);
        } else {
            candidates = new ArrayList<>(articleMusicRecommendMixService.buildPersonalizedRecommend(userId, 30));
        }
        MusicTrackVO featured = getFeatured();
        if (featured != null && StringUtils.hasText(featured.getMusicKey())) {
            String excludeKey = featured.getMusicKey().trim();
            candidates = candidates.stream()
                    .filter(item -> item != null && !Objects.equals(item.getMusicKey(), excludeKey))
                    .toList();
        }
        return toTrackPage(candidates, pageNum, pageSize);
    }

    @Override
    public PageResult<MusicHotTrackVO> pageHot(Integer pageNum, Integer pageSize) {
        int size = normalizePageSize(pageSize);
        int page = normalizePageNum(pageNum);
        long total = articleMusicHotRankingService.countHotMusicKeys();
        long pages = total == 0 ? 1 : (total + size - 1) / size;
        if (page > pages) {
            page = (int) pages;
        }
        int from = (page - 1) * size;
        List<String> keys = articleMusicHotRankingService.listHotMusicKeys(from, size);
        List<MusicTrackVO> tracks = loadTracksByKeys(keys);
        List<MusicHotTrackVO> records = new ArrayList<>(tracks.size());
        for (int i = 0; i < tracks.size(); i++) {
            MusicHotTrackVO hot = toHotTrack(tracks.get(i));
            hot.setRank(from + i + 1);
            records.add(hot);
        }
        return new PageResult<>(records, total, page, size, pages, (long) page < pages);
    }

    private List<MusicTrackVO> loadTracksByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<UserMusic> rows = userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .in(UserMusic::getMusicKey, keys)
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .ne(UserMusic::getDeleteState, Constant.DELETE_STATE_TRUE));
        Map<String, UserMusic> musicMap = new HashMap<>();
        for (UserMusic row : rows) {
            if (row != null && StringUtils.hasText(row.getMusicKey())) {
                musicMap.put(row.getMusicKey().trim(), row);
            }
        }
        Map<String, UserMusicPlayStat> statMap = loadStatMap(keys);
        List<MusicTrackVO> out = new ArrayList<>();
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            UserMusic row = musicMap.get(key.trim());
            if (row == null) {
                continue;
            }
            MusicTrackVO vo = UserMusicConverter.toTrackVO(row, false);
            if (vo == null) {
                continue;
            }
            UserMusicPlayStat stat = statMap.get(key.trim());
            long playCount = stat == null || stat.getPlayCount() == null ? 0L : stat.getPlayCount();
            vo.setPlayCount(playCount);
            vo.setPlayCountText(MusicPlayCountFormatter.format(playCount));
            out.add(vo);
        }
        return out;
    }

    private Map<String, UserMusicPlayStat> loadStatMap(List<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<UserMusicPlayStat> stats = userMusicPlayStatMapper.selectList(new LambdaQueryWrapper<UserMusicPlayStat>()
                .in(UserMusicPlayStat::getMusicKey, keys));
        Map<String, UserMusicPlayStat> map = new HashMap<>();
        for (UserMusicPlayStat stat : stats) {
            if (stat != null && StringUtils.hasText(stat.getMusicKey())) {
                map.put(stat.getMusicKey().trim(), stat);
            }
        }
        return map;
    }

    private static MusicHotTrackVO toHotTrack(MusicTrackVO source) {
        MusicHotTrackVO hot = new MusicHotTrackVO();
        hot.setId(source.getId());
        hot.setMusicKey(source.getMusicKey());
        hot.setTitle(source.getTitle());
        hot.setArtist(source.getArtist());
        hot.setAlbum(source.getAlbum());
        hot.setDurationText(source.getDurationText());
        hot.setCoverUrl(source.getCoverUrl());
        hot.setAudioUrl(source.getAudioUrl());
        hot.setLrcUrl(source.getLrcUrl());
        hot.setStatus(source.getStatus());
        hot.setMoodTags(source.getMoodTags());
        hot.setReviewReason(source.getReviewReason());
        hot.setReviewKind(source.getReviewKind());
        hot.setAiMatched(source.getAiMatched());
        hot.setFavorited(source.getFavorited());
        hot.setPlayCount(source.getPlayCount());
        hot.setPlayCountText(source.getPlayCountText());
        return hot;
    }

    private static PageResult<MusicTrackVO> toTrackPage(List<MusicTrackVO> all, Integer pageNum, Integer pageSize) {
        int size = normalizePageSize(pageSize);
        int page = normalizePageNum(pageNum);
        long total = all.size();
        long pages = total == 0 ? 1 : (total + size - 1) / size;
        if (page > pages) {
            page = (int) pages;
        }
        int from = (page - 1) * size;
        List<MusicTrackVO> records;
        if (from >= all.size()) {
            records = List.of();
        } else {
            records = all.subList(from, Math.min(from + size, all.size()));
        }
        return new PageResult<>(records, total, page, size, pages, (long) page < pages);
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }
}
