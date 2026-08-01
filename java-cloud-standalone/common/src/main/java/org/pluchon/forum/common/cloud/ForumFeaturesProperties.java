package org.pluchon.forum.common.cloud;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 各可启动服务的运行时能力开关，避免多实例重复消费 MQ / 重复调度 / 重复注册 WebSocket
@ConfigurationProperties(prefix = "forum.features")
public class ForumFeaturesProperties {

    // 是否启用 @Scheduled 任务（需配合具体任务上的条件或仅在目标域打开）
    private boolean scheduling = false;

    // 是否启用 RabbitMQ（拓扑声明、Template、Producer、自动装配前提）
    // false 时本进程不应连接 Broker；与 mq-consumer 区分：后者只控制 @RabbitListener
    private boolean mq = false;

    // 是否注册 RabbitMQ 消费者
    private boolean mqConsumer = false;

    // 是否注册 WebSocket Handler
    private boolean websocket = false;

    // 是否装配游戏对局运行时（含房间心跳调度）
    private boolean gameRuntime = false;

    public boolean isScheduling() {
        return scheduling;
    }

    public void setScheduling(boolean scheduling) {
        this.scheduling = scheduling;
    }

    public boolean isMq() {
        return mq;
    }

    public void setMq(boolean mq) {
        this.mq = mq;
    }

    public boolean isMqConsumer() {
        return mqConsumer;
    }

    public void setMqConsumer(boolean mqConsumer) {
        this.mqConsumer = mqConsumer;
    }

    public boolean isWebsocket() {
        return websocket;
    }

    public void setWebsocket(boolean websocket) {
        this.websocket = websocket;
    }

    public boolean isGameRuntime() {
        return gameRuntime;
    }

    public void setGameRuntime(boolean gameRuntime) {
        this.gameRuntime = gameRuntime;
    }
}
