package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.UserMusic;

@Mapper
public interface UserMusicMapper extends BaseMapper<UserMusic> {

    /**
     * 曲库分页：已发布 + AI 画像就绪，按播放量降序。
     *
     * <p>原来用 LambdaQueryWrapper 按歌名字母序排，于是「热门」这个默认档位名不副实。
     * 播放量在 user_music_play_stat 里，跨表排序 Wrapper 表达不了，所以下沉成手写 SQL。
     */
    @Select("""
            <script>
            SELECT m.* FROM user_music m
            LEFT JOIN user_music_play_stat s ON s.music_key = m.music_key
             WHERE m.status = #{publishedStatus}
               AND m.delete_state = 0
               AND m.ai_profile IS NOT NULL
               AND m.ai_profile &lt;&gt; ''
               <if test="mood != null">
                 AND JSON_VALID(m.mood_tags)
                 AND JSON_CONTAINS(CAST(m.mood_tags AS JSON), JSON_QUOTE(#{mood}))
               </if>
               <if test="keyword != null">
                 <choose>
                   <when test="scope == 'title'">AND m.title LIKE CONCAT('%', #{keyword}, '%')</when>
                   <when test="scope == 'artist'">AND m.artist LIKE CONCAT('%', #{keyword}, '%')</when>
                   <when test="scope == 'album'">AND m.album LIKE CONCAT('%', #{keyword}, '%')</when>
                   <otherwise>
                     AND (m.title LIKE CONCAT('%', #{keyword}, '%')
                       OR m.artist LIKE CONCAT('%', #{keyword}, '%')
                       OR m.album LIKE CONCAT('%', #{keyword}, '%')
                       OR m.music_key LIKE CONCAT('%', #{keyword}, '%'))
                   </otherwise>
                 </choose>
               </if>
             ORDER BY COALESCE(s.play_count, 0) DESC, m.id DESC
            </script>
            """)
    IPage<UserMusic> selectCatalogPage(IPage<UserMusic> page,
                                       @Param("publishedStatus") byte publishedStatus,
                                       @Param("mood") String mood,
                                       @Param("keyword") String keyword,
                                       @Param("scope") String scope);
}
