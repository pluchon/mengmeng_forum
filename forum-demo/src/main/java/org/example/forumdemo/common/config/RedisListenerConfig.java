package org.example.forumdemo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// 主要是处理消息的广播，解决连接和请求不在一台服务器上的痛点
// 因为 WebSocket 的物理连接（Session）是保存在单机 JVM 内存中的，无法序列化存入 Redis 共享
// 因此我们采用了 Redis 的发布/订阅（Pub/Sub）机制作为‘消息广播总线’
// 当任意节点需要推送消息时，会将‘推送指令（含用户 ID 和内容）’发布到 Redis 频道
// 所有订阅了该频道的服务节点都会收到该广播，并在各自本地的 Session 注册表中检索
// 如果用户恰好连接在当前节点，则由该节点执行本地推送
// 通过这种‘广播通知指令’的方式，我们完美实现了 WebSocket 集群的横向扩展
@Configuration
public class RedisListenerConfig {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
