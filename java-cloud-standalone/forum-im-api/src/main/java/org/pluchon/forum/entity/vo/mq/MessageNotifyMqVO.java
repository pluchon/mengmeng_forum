package org.pluchon.forum.entity.vo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author pluchon
 * @create 2026-04-06
 *         作者代码水平一般，难免难看，请见谅
 *
 *         私信通知消息 VO，投递到 MQ 队列 2（q-queue_2）
 *         消费者通过此对象感知"谁"发了私信给"谁"，并通知接收者
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageNotifyMqVO {
    // 消息唯一 ID，用于消费者做幂等性去重（后续接入 Redis 校验）
    private String messageId;
    // 数据库中的站内信 ID，消费者可凭此回查完整内容
    private Long dbMessageId;
    // 发送者用户 ID
    private Long sendUserId;
    // 发送者用户名，消费者无需再查库即可渲染通知文案
    private String sendUsername;
    // 接收者用户 ID（需要被 WebSocket 通知的目标用户）
    private Long receiveUserId;
    // 私信内容摘要（截取前 50 字即可，通知文案用）
    private String contentSummary;
    // 消息生成时间戳（毫秒）
    private Long timestamp;
}
