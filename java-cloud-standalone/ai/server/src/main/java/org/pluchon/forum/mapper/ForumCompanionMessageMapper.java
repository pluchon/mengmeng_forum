package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.ForumCompanionMessage;

@Mapper
public interface ForumCompanionMessageMapper extends BaseMapper<ForumCompanionMessage> {

    /**
     * 会话里参与上下文的正文字符数。
     *
     * <p>只为了画那条进度条，原来要把整个会话的消息全查回 Java 再逐条数长度，
     * 而这个查询在「打开面板」「发送前」「流结束」各跑一次。交给数据库聚合。
     *
     * @param afterId 最近一条压缩摘要的 id；没有摘要时传 0
     */
    @Select("SELECT COALESCE(SUM(CHAR_LENGTH(content)), 0) FROM forum_companion_message "
            + "WHERE session_id = #{sessionId} AND delete_state = 0 "
            + "AND msg_type <> 'context_summary' AND id > #{afterId} "
            + "AND content IS NOT NULL")
    Long sumContentLength(@Param("sessionId") Long sessionId, @Param("afterId") Long afterId);
}
