package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleMusicCatalogServiceImpl implements ArticleMusicCatalogService {

    @Autowired
    private UserMusicMapper userMusicMapper;


    @Override
    public PageResult<MusicTrackVO> pageCatalog(String keyword, String scope, String mood,
                                                Integer pageNum, Integer pageSize) {
        int size = pageSize == null || pageSize < 1 ? Constant.MUSIC_CATALOG_PAGE_SIZE : Math.min(pageSize, 50);
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        String kw = keyword == null ? "" : keyword.trim();
        String scopeKey = normalizeScope(scope);
        String moodFilter = mood == null ? "" : mood.trim();
        // 「热门」是默认态而非真实氛围，当作不过滤
        String effectiveMood = StringUtils.hasText(moodFilter) && !"热门".equals(moodFilter) ? moodFilter : null;

        // 过滤、排序、分页都下推到 SQL。之前是把整个已发布曲库装进内存再 subList，
        // 歌一多就是全表扫 + 全量对象化；排序也从歌名字母序改成播放量降序，
        // 否则「热门」这个默认档位只是「不过滤」的别名。
        Page<UserMusic> mpPage = new Page<>(page, size);
        IPage<UserMusic> result = userMusicMapper.selectCatalogPage(
                mpPage,
                Constant.USER_MUSIC_STATUS_PUBLISHED,
                effectiveMood,
                StringUtils.hasText(kw) ? kw : null,
                scopeKey);
        List<MusicTrackVO> records = result.getRecords().stream()
                .map(row -> UserMusicConverter.toTrackVO(row, false))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), (int) result.getCurrent(),
                (int) result.getSize(), result.getPages(), result.getCurrent() < result.getPages());
    }

    private static String normalizeScope(String scope) {
        String s = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if ("title".equals(s) || "artist".equals(s) || "album".equals(s)) {
            return s;
        }
        return "all";
    }
}
