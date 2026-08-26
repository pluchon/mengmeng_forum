package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.dto.article.ToggleMusicFavoriteRequest;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

// 用户上传歌曲、我的列表与收藏
public interface ArticleUserMusicService {

    MusicTrackVO upload(Long userId, String action, Long id, String title, String artist, String album,
                        String durationText, String lyricText, String moodTags,
                        MultipartFile audio, MultipartFile cover, MultipartFile lrc);

    List<MusicTrackVO> listMine(Long userId, String scope);

    List<MusicTrackVO> listFavorites(Long userId);

    boolean toggleFavorite(Long userId, ToggleMusicFavoriteRequest req);

    PageResult<MusicTrackVO> pageRecentPlays(Long userId, Integer pageNum, Integer pageSize);

    void recordPlay(Long userId, ToggleMusicFavoriteRequest req);

    void markFavorited(Long userId, List<MusicTrackVO> tracks);

    Map<String, UserMusic> findByMusicKeys(Collection<String> musicKeys);

    List<UserMusic> listPublished();

    List<UserMusic> listPublishedWithAiProfile();

    boolean isBindable(String musicKey);

    MusicTrackVO retryAudit(Long userId, Long id);
}
