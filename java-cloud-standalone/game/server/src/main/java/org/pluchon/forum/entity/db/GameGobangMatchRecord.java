package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 五子棋对局记录，一局结束后只写入一条
@Data
@TableName("game_gobang_match_record")
public class GameGobangMatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roomId;

    private Long blackUserId;

    private Long whiteUserId;

    private Long winnerUserId;

    private Long loserUserId;

    private String endReason;

    private Integer scoreDelta;

    // 胜方本局排位分变化
    private Integer winnerScoreDelta;

    // 败方本局排位分变化，负数
    private Integer loserScoreDelta;

    private Date startedAt;

    private Date endedAt;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
