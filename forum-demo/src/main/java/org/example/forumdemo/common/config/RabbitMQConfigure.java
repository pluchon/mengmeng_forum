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

    // 仲裁队列 3：Java 投递的帖子异步审核任务
    // maxLength 给 200, 短时段批量发布能扛住积压; 单条 payload 包含 articleId / 文本 / 图片 URL 数组
    @Bean("q-audit-article")
    public Queue auditArticleQueue() {
        return QueueBuilder.durable(Constant.QUORUM_QUEUE_AUDIT_TASK)
                .maxLength(200).quorum()
                .deadLetterExchange(Constant.DEATH_EXCHANGE_1)
                .deadLetterRoutingKey(Constant.ROUTING_KEY_DEAD)
                .build();
    }

    // 仲裁队列 4：Python 回执 Java 的审核结果
    @Bean("q-audit-result")
    public Queue auditResultQueue() {
        return QueueBuilder.durable(Constant.QUORUM_QUEUE_AUDIT_RESULT)
                .maxLength(200).quorum()
                .deadLetterExchange(Constant.DEATH_EXCHANGE_1)
                .deadLetterRoutingKey(Constant.ROUTING_KEY_DEAD)
                .build();
    }

    // 死信队列：统一存储流出的死信消息，普通持久化队列即可。
    // 现阶段小项目共用一条死信队列即可：上游用不同的业务 RoutingKey
    // (forum.notify.reply / forum.notify.message) 进入仲裁队列后，
    // 即使死信进入死信交换机，消费者也能根据原始 routing key 区分来源。
    // 如果未来某一类死信业务量明显增大或处理逻辑差异较大，再拆成两条独立的死信队列。
    @Bean("d-queue")
    public Queue deathQueue() {
        return QueueBuilder.durable(Constant.D_QUORUM_QUEUE_1).build();
    }

    // ========================= 绑定 =========================
    // 帖子回复模块
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

    // 帖子审核任务: 仲裁队列3 <- 主题交换机, RoutingKey = forum.audit.article
    @Bean("binding-audit-task")
    public Binding bindingAuditTaskQueue() {
        return BindingBuilder.bind(auditArticleQueue()).to(topicExchange()).with(Constant.ROUTING_KEY_AUDIT_TASK);
    }

    // 帖子审核结果: 仲裁队列4 <- 主题交换机, RoutingKey = forum.audit.result
    @Bean("binding-audit-result")
    public Binding bindingAuditResultQueue() {
        return BindingBuilder.bind(auditResultQueue()).to(topicExchange()).with(Constant.ROUTING_KEY_AUDIT_RESULT);
    }

    // 死信队列 -> 死信交换机，RoutingKey = forum.dead.#
    // 我们目前先接收所有业务类型的死信
    @Bean("binding-dead")
    public Binding bindingDeadQueue() {
        return BindingBuilder.bind(deathQueue()).to(deathExchange()).with(Constant.ROUTING_KEY_DEAD);
    }
}
