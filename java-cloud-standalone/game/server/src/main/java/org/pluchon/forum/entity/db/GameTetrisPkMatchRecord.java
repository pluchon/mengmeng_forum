package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 俄罗斯方块 PK 对局记录
@Data
@TableName("game_tetris_pk_match_record")
public class GameTetrisPkMatchRecord {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 房间 ID
    private String roomId;

    // 玩家1用户 ID
    private Long player1UserId;

    // 玩家2用户 ID
    private Long player2UserId;

    // 红方用户 ID
    private Long redUserId;

    // 蓝方用户 ID
    private Long blueUserId;

    // 胜方用户 ID
    private Long winnerUserId;

    // 败方用户 ID
    private Long loserUserId;

    // 玩家1得分
    private Integer player1Score;

    // 玩家2得分
    private Integer player2Score;

    // 玩家1消行数：竞速的胜负先看它
    private Integer player1Lines;

    // 玩家2消行数
    private Integer player2Lines;

    // 结束原因
    private String endReason;

    // 积分变动
    private Integer scoreDelta;

    // 胜方本局排位分变化
    private Integer winnerScoreDelta;

    // 败方本局排位分变化，负数
    private Integer loserScoreDelta;

    // 回放 JSON
    private String replayPayload;

    // 开始时间
    private Date startedAt;

    // 结束时间
    private Date endedAt;

    // 是否删除
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
