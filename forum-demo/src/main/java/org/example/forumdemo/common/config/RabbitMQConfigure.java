package org.example.forumdemo.common.config;

import org.example.forumdemo.common.constant.Constant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfigure {

    // ========================= 交换机 =========================
    // 主题交换机：业务消息入口，按 RoutingKey 路由到各业务队列
    @Bean("t-exchange")
    public TopicExchange topicExchange() {
        return ExchangeBuilder.topicExchange(Constant.TOPIC_EXCHANGE_1).durable(true).build();
    }

    // 死信交换机：统一接收过期/溢出/nack 的死信消息
    @Bean("d-exchange")
    public TopicExchange deathExchange() {
        return ExchangeBuilder.topicExchange(Constant.DEATH_EXCHANGE_1).durable(true).build();
    }

    // ========================= 队列 =========================
    // 使用仲裁队列可以达到很好的流量削峰效果，而且也能够自动归档到死信队列，后续也可以拓展我们的分布式

    // 仲裁队列 1：帖子回复通知，最多积压 100 条，超限则死信，这样就保证了我们的数据安全与高可用状态
    @Bean("q-queue-1")
    public Queue quorumQueue1() {
        return QueueBuilder.durable(Constant.QUORUM_QUEUE_1)
                .maxLength(100).quorum()
                .deadLetterExchange(Constant.DEATH_EXCHANGE_1)
                .deadLetterRoutingKey(Constant.ROUTING_KEY_DEAD)
                .build();
    }

    // 仲裁队列 2：私信通知，配置与队列 1 相同
    @Bean("q-queue-2")
    public Queue quorumQueue2() {
        return QueueBuilder.durable(Constant.QUORUM_QUEUE_2)
                .maxLength(100).quorum()
                .deadLetterExchange(Constant.DEATH_EXCHANGE_1)
                .deadLetterRoutingKey(Constant.ROUTING_KEY_DEAD)
                .build();
    }

    // 我们的消息审核机制没有走队列，直接走了HTTP的请求

    // 仲裁队列 3：Java 投递的帖子异步审核任务，只给200，防止我们服务端压力过大
    @Bean("q-audit-article")
    public Queue auditArticleQueue() {
        return QueueBuilder.durable(Constant.QUORUM_QUEUE_AUDIT_TASK)
                .maxLength(200).quorum()
                .deadLetterExchange(Constant.DEATH_EXCHANGE_1)
                .deadLetterRoutingKey(Constant.ROUTING_KEY_DEAD)
                .build();
    }

    // 仲裁队列 4：langgraph 返回的结果，投递给这个队列，Java端可以进行处理
    @Bean("q-audit-result")
    public Queue auditResultQueue() {
        return QueueBuilder.durable(Constant.QUORUM_QUEUE_AUDIT_RESULT)
                .maxLength(200).quorum()
                .deadLetterExchange(Constant.DEATH_EXCHANGE_1)
                .deadLetterRoutingKey(Constant.ROUTING_KEY_DEAD)
                .build();
    }

    // 死信队列：统一存储流出的死信消息，普通持久化队列即可，通过routing key 进行区分
    @Bean("d-queue")
    public Queue deathQueue() {
        return QueueBuilder.durable(Constant.D_QUORUM_QUEUE_1).build();
    }

    // ========================= 绑定 =========================

    // 帖子回复
    // 仲裁队列1 -> 主题交换机，RoutingKey = forum.notify.reply
    @Bean("binding-queue-1")
    public Binding bindingQueue1() {
        return BindingBuilder.bind(quorumQueue1()).to(topicExchange()).with(Constant.ROUTING_KEY_QUEUE_1);
    }

    // 私信模块
    // 仲裁队列2 -> 主题交换机，RoutingKey = forum.notify.message
    @Bean("binding-queue-2")
    public Binding bindingQueue2() {
        return BindingBuilder.bind(quorumQueue2()).to(topicExchange()).with(Constant.ROUTING_KEY_QUEUE_2);
    }

    // Java发送给langgraph任务，通过主题交换机路由到对应的队列
    // 帖子审核任务: 仲裁队列3 <- 主题交换机, RoutingKey = forum.audit.article
    @Bean("binding-audit-task")
    public Binding bindingAuditTaskQueue() {
        return BindingBuilder.bind(auditArticleQueue()).to(topicExchange()).with(Constant.ROUTING_KEY_AUDIT_TASK);
    }

    // 处理langgraph返回的结果，Python端通过交换机路由到对应的队列
    // 帖子审核结果: 仲裁队列4 <- 主题交换机, RoutingKey = forum.audit.result
    @Bean("binding-audit-result")
    public Binding bindingAuditResultQueue() {
        return BindingBuilder.bind(auditResultQueue()).to(topicExchange()).with(Constant.ROUTING_KEY_AUDIT_RESULT);
    }

    // 死信队列 -> 死信交换机，RoutingKey = forum.dead.#
    @Bean("binding-dead")
    public Binding bindingDeadQueue() {
        return BindingBuilder.bind(deathQueue()).to(deathExchange()).with(Constant.ROUTING_KEY_DEAD);
    }
}
