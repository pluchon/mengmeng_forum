package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 游戏对局记录，一局结束后只写入一条
@Data
@TableName("game_match_record")
public class GameMatchRecord {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 游戏编码，如 gobang
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 黑方用户 ID
    private Long blackUserId;

    // 白方用户 ID
    private Long whiteUserId;

    // 胜方用户 ID，异常结束时可为空
    private Long winnerUserId;

    // 负方用户 ID，异常结束时可为空
    private Long loserUserId;

    // 结束原因：FIVE / SURRENDER / DISCONNECT / TIMEOUT / ABNORMAL
    private String endReason;

    // 本局胜负积分变化绝对值
    private Integer scoreDelta;

    // 对局开始时间
    private Date startedAt;

    // 对局结束时间
    private Date endedAt;

    // 是否删除：0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
