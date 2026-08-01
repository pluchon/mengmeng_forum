package org.example.forumdemo.common.constant;

// RabbitMQ 交换机、队列与路由键
public final class MqConstants {

    // 主题交换机：负责将业务消息按 RoutingKey 路由到对应队列
    public static final String TOPIC_EXCHANGE_1 = "t_exchange_1";
    // 死信交换机：接收从业务队列中过期或被拒绝的死信消息
    public static final String DEATH_EXCHANGE_1 = "d-exchange_1";

    // 业务仲裁队列 1：帖子回复通知等实时消息
    public static final String QUORUM_QUEUE_1 = "q-queue_1";
    // 业务仲裁队列 2：私信消息
    public static final String QUORUM_QUEUE_2 = "q-queue_2";
    // Java -> Python 帖子异步审核任务
    public static final String QUORUM_QUEUE_AUDIT_TASK = "q-audit-article";
    // Python -> Java 帖子审核结果回执
    public static final String QUORUM_QUEUE_AUDIT_RESULT = "q-audit-result";
    // 游戏对局结束事件
    public static final String QUORUM_QUEUE_GAME_FINISHED = "q-game-finished";
    // 死信队列
    public static final String D_QUORUM_QUEUE_1 = "d-queue_1";

    public static final String ROUTING_KEY_QUEUE_1 = "forum.notify.reply";
    public static final String ROUTING_KEY_QUEUE_2 = "forum.notify.message";
    public static final String ROUTING_KEY_AUDIT_TASK = "forum.audit.article";
    public static final String ROUTING_KEY_AUDIT_RESULT = "forum.audit.result";
    public static final String ROUTING_KEY_GAME_FINISHED = "forum.game.finished";
    public static final String ROUTING_KEY_DEAD = "forum.dead.#";

    private MqConstants() {
    }
}
