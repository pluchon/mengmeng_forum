package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.UserMusicPlayHistory;

@Mapper
public interface UserMusicPlayHistoryMapper extends BaseMapper<UserMusicPlayHistory> {

    /**
     * 原曲改名换封面后，快照里还是旧的。
     *
     * <p>只刷展示字段：audio_url / lrc_url 正是快照要保住的东西，原曲哪天没了，
     * 收藏的人还能靠它继续听。
     */
    @Update("UPDATE user_music_play_history SET title = #{title}, artist = #{artist}, album = #{album}, "
            + "cover_url = #{coverUrl} WHERE music_key = #{musicKey} AND delete_state = 0")
    int refreshSnapshot(@Param("musicKey") String musicKey,
                        @Param("title") String title,
                        @Param("artist") String artist,
                        @Param("album") String album,
                        @Param("coverUrl") String coverUrl);
}
