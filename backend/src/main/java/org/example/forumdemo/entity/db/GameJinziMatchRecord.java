package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 井字棋对局记录，一局结束后只写入一条
@Data
@TableName("game_jinzi_match_record")
public class GameJinziMatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roomId;

    private Long blackUserId;

    private Long whiteUserId;

    private Long winnerUserId;

    private Long loserUserId;

    private String endReason;

    private Integer scoreDelta;

    private Date startedAt;

    private Date endedAt;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
