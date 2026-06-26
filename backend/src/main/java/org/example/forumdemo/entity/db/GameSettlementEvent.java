package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 游戏结算事件表，用于记录对局结束后的 MQ 投递和消费幂等状态
@Data
@TableName("game_settlement_event")
public class GameSettlementEvent {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 事件唯一 ID，用于 MQ 投递和消费端幂等
    private String eventId;

    // 游戏编码，如 gobang
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 事件类型，如 GAME_FINISHED
    private String eventType;

    // 关联的对局记录 ID
    private Long recordId;

    // 事件状态：CREATED/MQ_SENT/MQ_PENDING/CONSUMED/DEAD
    private String status;

    // 重试次数
    private Integer retryCount;

    // 最近一次错误摘要
    private String lastError;

    // 是否删除：0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
