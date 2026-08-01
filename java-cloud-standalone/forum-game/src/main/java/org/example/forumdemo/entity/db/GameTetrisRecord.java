package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 俄罗斯方块单人局记录
@Data
@TableName("game_tetris_record")
public class GameTetrisRecord {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户 ID
    private Long userId;

    // 游戏编码
    private String gameCode;

    // 本局分数
    private Integer score;

    // 结束时等级
    private Integer level;

    // 总消行数
    private Integer linesCleared;

    // 局时长毫秒
    private Long durationMs;

    // 随机种子
    private Long seed;

    // 回放 JSON
    private String replayPayload;

    // 本次论坛积分奖励
    private Integer forumPointsAwarded;

    // 校验状态
    private String validationStatus;

    // 开局时间
    private Date startedAt;

    // 结束时间
    private Date endedAt;

    // 是否删除：0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
