package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.constant.ForumTimeZone;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 音乐大厅发现页：今日精选、推荐、热榜（热榜读 Redis）
@Service
public class ArticleMusicDiscoverServiceImpl implements ArticleMusicDiscoverService {

    private static final int DEFAULT_PAGE_SIZE = 6;
    // 今日精选的候选池：太小则天天重复，太大会选到几乎没人听的曲子
    private static final int FEATURED_POOL_SIZE = 20;
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

    /**
     * 今日精选。
     *
     * <p>原来直接取热榜第 1 名，于是同一个发现页上「今日精选」和「本周热榜」榜首
     * 永远是同一首歌，两张卡片重复；而且叫「今日」却没有任何按日轮换的成分。
     * 现在从热榜前 {@value #FEATURED_POOL_SIZE} 名里按当天日期取一首，跳过榜首：
     * 一天之内稳定，隔天会换，也不会和热榜第一撞车。
     */
    @Override
    public MusicTrackVO getFeatured() {
        List<String> pool = articleMusicHotRankingService.listHotMusicKeys(0, FEATURED_POOL_SIZE);
        if (pool.isEmpty()) {
            return null;
        }
        // 榜首留给热榜，池子里还有别的就从第 2 名开始挑
        List<String> candidates = pool.size() > 1 ? pool.subList(1, pool.size()) : pool;
        long day = LocalDate.now(ForumTimeZone.ZONE_ID).toEpochDay();
        int idx = (int) Math.floorMod(day, candidates.size());
        List<MusicTrackVO> tracks = loadTracksByKeys(List.of(candidates.get(idx)));
        if (!tracks.isEmpty()) {
            return tracks.get(0);
        }
        // 选中的那首可能刚被下架，退回池子里第一首能取到的
        List<MusicTrackVO> fallback = loadTracksByKeys(pool);
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    @Override
    public PageResult<MusicTrackVO> pageRecommend(Long userId, List<String> moods,
                                                  Integer pageNum, Integer pageSize) {
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
        candidates = applyMoodFilter(candidates, moods);
        return toTrackPage(candidates, pageNum, pageSize);
    }

    /**
     * 按勾选的氛围标签过滤并重排推荐候选。
     *
     * <p>召回用 OR：标签是 AI 打的，同时要求「深夜」且「伤感」经常直接空集。
     * 但 OR 会让大量弱相关的歌涌进来，所以排序上做三级惩罚：
     * 命中数多的优先 → 同命中数按播放量 → 同一作者在一页里限量，
     * 否则一个高产作者能把整页占满。
     */
    private List<MusicTrackVO> applyMoodFilter(List<MusicTrackVO> candidates, List<String> moods) {
        List<String> wanted = new ArrayList<>();
        for (String mood : moods == null ? List.<String>of() : moods) {
            String name = mood == null ? "" : mood.trim();
            if (!name.isEmpty() && !wanted.contains(name)) {
                wanted.add(name);
            }
            if (wanted.size() >= Constant.MUSIC_MOOD_FILTER_MAX) {
                break;
            }
        }
        if (wanted.isEmpty() || candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        List<MusicTrackVO> hit = new ArrayList<>();
        Map<String, Integer> hitCount = new HashMap<>();
        for (MusicTrackVO track : candidates) {
            int count = countMoodHits(track, wanted);
            if (count > 0) {
                hit.add(track);
                hitCount.put(track.getMusicKey(), count);
            }
        }
        if (hit.isEmpty()) {
            return hit;
        }
        hit.sort(Comparator
                .comparingInt((MusicTrackVO t) -> -hitCount.getOrDefault(t.getMusicKey(), 0))
                .thenComparing(t -> -(t.getPlayCount() == null ? 0L : t.getPlayCount())));
        return capPerAuthor(hit, loadAuthorMap(hit));
    }

    private static int countMoodHits(MusicTrackVO track, List<String> wanted) {
        List<String> tags = track == null ? null : track.getMoodTags();
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String wantedTag : wanted) {
            if (tags.contains(wantedTag)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 只在筛选生效时才查一次 musicKey → 作者。
     *
     * <p>不把 userId 加进 MusicTrackVO：那是对外的展示模型，没必要把上传者 ID 下发给前端。
     */
    private Map<String, Long> loadAuthorMap(List<MusicTrackVO> tracks) {
        List<String> keys = new ArrayList<>();
        for (MusicTrackVO track : tracks) {
            if (track != null && StringUtils.hasText(track.getMusicKey())) {
                keys.add(track.getMusicKey().trim());
            }
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<UserMusic> rows = userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .select(UserMusic::getMusicKey, UserMusic::getUserId)
                .in(UserMusic::getMusicKey, keys));
        Map<String, Long> map = new HashMap<>();
        for (UserMusic row : rows) {
            if (row != null && StringUtils.hasText(row.getMusicKey())) {
                map.put(row.getMusicKey().trim(), row.getUserId());
            }
        }
        return map;
    }

    // 同一作者在结果里限量，避免高产作者霸屏。热榜那边早有同样的约束
    private static List<MusicTrackVO> capPerAuthor(List<MusicTrackVO> tracks, Map<String, Long> authorMap) {
        Map<Long, Integer> perAuthor = new HashMap<>();
        List<MusicTrackVO> out = new ArrayList<>(tracks.size());
        List<MusicTrackVO> overflow = new ArrayList<>();
        for (MusicTrackVO track : tracks) {
            Long author = authorMap.get(track.getMusicKey());
            if (author == null) {
                out.add(track);
                continue;
            }
            int used = perAuthor.getOrDefault(author, 0);
            if (used < Constant.MUSIC_HOT_AUTHOR_MAX_PER_LIST) {
                perAuthor.put(author, used + 1);
                out.add(track);
            } else {
                // 超额的不丢弃，挪到末尾——筛选结果本来就不多时全砍掉会很空
                overflow.add(track);
            }
        }
        out.addAll(overflow);
        return out;
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
