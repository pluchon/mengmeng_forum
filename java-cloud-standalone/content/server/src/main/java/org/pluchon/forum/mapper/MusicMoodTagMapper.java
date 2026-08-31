package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.MusicMoodTag;

@Mapper
public interface MusicMoodTagMapper extends BaseMapper<MusicMoodTag> {

    /**
     * 标签存在就累加使用次数，不存在就按给定来源建一条。
     * 并发下靠 uk_music_mood_tag_name 收敛，不用先查后插。
     */
    @Update("INSERT INTO music_mood_tag (name, source, create_user_id, use_count, enabled, delete_state) "
            + "VALUES (#{name}, #{source}, #{createUserId}, 1, 1, 0) "
            + "ON DUPLICATE KEY UPDATE use_count = use_count + 1")
    int upsertAndTouch(@Param("name") String name,
                       @Param("source") String source,
                       @Param("createUserId") Long createUserId);

    /** 创作者建标签：只建不计数，等真有歌用了再涨 */
    @Update("INSERT INTO music_mood_tag (name, source, create_user_id, use_count, enabled, delete_state) "
            + "VALUES (#{name}, 'USER', #{createUserId}, 0, 1, 0) "
            + "ON DUPLICATE KEY UPDATE update_time = update_time")
    int insertIfAbsent(@Param("name") String name, @Param("createUserId") Long createUserId);
}
