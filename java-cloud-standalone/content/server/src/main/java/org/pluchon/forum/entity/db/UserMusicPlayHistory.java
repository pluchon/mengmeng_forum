package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 用户歌曲最近播放
@Data
@TableName("user_music_play_history")
@Schema(description = "用户歌曲最近播放")
public class UserMusicPlayHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String musicKey;

    private String title;

    private String artist;

    private String album;

    private String durationText;

    private String coverUrl;

    private String audioUrl;

    private String lrcUrl;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
