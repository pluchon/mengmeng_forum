package org.example.forumdemo.entity.vo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author pluchon
 * @create 2026-04-06
 *         作者代码水平一般，难免难看，请见谅
 *
 *         帖子回复通知消息 VO，投递到 MQ 队列 1（q-queue_1）
 *         消费者通过此对象感知"谁"回复了"哪篇帖子"，并通知帖子作者
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplyNotifyMqVO {
    // 消息唯一 ID，用于消费者做幂等性去重（后续接入 Redis 校验）
    private String messageId;
    // 被回复的帖子 ID
    private Long articleId;
    // 发出回复动作的用户 ID（回复者）
    private Long postUserId;
    // 发出回复动作的用户名（回复者），消费者无需再查库
    private String postUsername;
    // 被通知的目标用户 ID（帖子作者 或 楼中楼的被回复者）
    // 对应 ArticleReply.replyUserId，如果是一级回复则填帖子作者 ID
    private Long notifyUserId;
    // 回复内容摘要（截取前 50 字即可，通知文案用）
    private String contentSummary;
    // 消息生成时间戳（毫秒），消费者可据此设置消息 TTL 或排序
    private Long timestamp;
}
