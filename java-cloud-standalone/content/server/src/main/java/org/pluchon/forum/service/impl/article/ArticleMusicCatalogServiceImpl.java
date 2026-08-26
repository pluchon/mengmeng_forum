package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.converter.UserMusicConverter;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class ArticleMusicCatalogServiceImpl implements ArticleMusicCatalogService {

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResult<MusicTrackVO> pageCatalog(String keyword, String scope, String mood,
                                                Integer pageNum, Integer pageSize) {
        List<MusicTrackVO> all = listVisibleCatalog(keyword, scope, mood);
        int size = pageSize == null || pageSize < 1 ? Constant.MUSIC_CATALOG_PAGE_SIZE : Math.min(pageSize, 50);
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
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
            int to = Math.min(from + size, all.size());
            records = all.subList(from, to);
        }
        return new PageResult<>(records, total, page, size, pages, (long) page < pages);
    }

    private List<MusicTrackVO> listVisibleCatalog(String keyword, String scope, String mood) {
        List<UserMusic> rows = userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .isNotNull(UserMusic::getAiProfile)
                .ne(UserMusic::getAiProfile, "")
                .ne(UserMusic::getDeleteState, 1)
                .orderByDesc(UserMusic::getUpdateTime));
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String scopeKey = normalizeScope(scope);
        String moodFilter = mood == null ? "" : mood.trim();
        boolean filterMood = StringUtils.hasText(moodFilter) && !"热门".equals(moodFilter);
        List<MusicTrackVO> tracks = new ArrayList<>();
        for (UserMusic row : rows) {
            MusicTrackVO vo = UserMusicConverter.toTrackVO(row, false);
            if (filterMood && !containsMoodTag(row.getMoodTags(), moodFilter)) {
                continue;
            }
            if (StringUtils.hasText(kw) && !matchesKeyword(vo, kw, scopeKey)) {
                continue;
            }
            tracks.add(vo);
        }
        tracks.sort(Comparator.comparing(MusicTrackVO::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return tracks;
    }

    private boolean containsMoodTag(String raw, String mood) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(mood)) {
            return false;
        }
        try {
            List<String> tags = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            if (tags == null) {
                return false;
            }
            for (String tag : tags) {
                if (mood.equals(tag)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return raw.contains(mood);
        }
        return false;
    }

    private static String normalizeScope(String scope) {
        String s = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if ("title".equals(s) || "artist".equals(s) || "album".equals(s)) {
            return s;
        }
        return "all";
    }

    private static boolean matchesKeyword(MusicTrackVO vo, String kw, String scope) {
        if ("title".equals(scope)) {
            return contains(vo.getTitle(), kw);
        }
        if ("artist".equals(scope)) {
            return contains(vo.getArtist(), kw);
        }
        if ("album".equals(scope)) {
            return contains(vo.getAlbum(), kw);
        }
        return contains(vo.getTitle(), kw)
                || contains(vo.getArtist(), kw)
                || contains(vo.getAlbum(), kw)
                || contains(vo.getMusicKey(), kw);
    }

    private static boolean contains(String value, String kw) {
        if (!StringUtils.hasText(kw)) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }
}
