package org.pluchon.forum.entity.vo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏对局结束事件 MQ 载荷，结算事务提交后投递给异步通知、统计和榜单链路
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameFinishedMqVO {

    // 事件唯一 ID，用于消费者幂等
    private String eventId;

    // 游戏编码
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 对局记录 ID
    private Long recordId;

    // 胜方用户 ID
    private Long winnerUserId;

    // 负方用户 ID
    private Long loserUserId;

    // 结束原因
    private String endReason;
}
