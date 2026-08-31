package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.dto.article.ToggleMusicFavoriteRequest;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 用户上传歌曲、我的列表与收藏
public interface ArticleUserMusicService {

    MusicTrackVO upload(Long userId, String action, Long id, String title, String artist, String album,
                        String durationText, String lyricText, String moodTags,
                        MultipartFile audio, MultipartFile cover, MultipartFile lrc);

    /**
     * 我的上传 / 我的发布。
     *
     * <p>状态筛选与关键词一并下推到 SQL：分页留在后端而筛选留在前端的话，
     * 会出现「第 2 页搜不到第 1 页的歌」。
     */
    PageResult<MusicTrackVO> pageMine(Long userId, String scope, String status, String keyword,
                                      Integer pageNum, Integer pageSize);

    PageResult<MusicTrackVO> pageFavorites(Long userId, Integer pageNum, Integer pageSize);

    boolean toggleFavorite(Long userId, ToggleMusicFavoriteRequest req);

    PageResult<MusicTrackVO> pageRecentPlays(Long userId, Integer pageNum, Integer pageSize);

    void recordPlay(Long userId, ToggleMusicFavoriteRequest req);

    void markFavorited(Long userId, List<MusicTrackVO> tracks);

    List<UserMusic> listPublishedWithAiProfile();

    boolean isBindable(String musicKey);

    MusicTrackVO retryAudit(Long userId, Long id);
}
