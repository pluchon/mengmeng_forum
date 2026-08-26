package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 歌曲全站播放统计
@Data
@TableName("user_music_play_stat")
@Schema(description = "歌曲全站播放统计")
public class UserMusicPlayStat {

    @TableId
    private String musicKey;

    private Long playCount;

    private Long weeklyPlayCount;

    private Date weekStart;

    private Date updateTime;
}
