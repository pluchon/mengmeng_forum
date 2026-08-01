package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** MQ 本地消息表：与业务同事务写入，异步投递 RabbitMQ */
@Data
@TableName("forum_outbox_message")
public class ForumOutboxMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String routingKey;

    private String payloadJson;

    /** 0=待投递 1=已投递 2=已消费 3=失败 4=死信 */
    private Integer messageState;

    private Integer retryCount;

    private String lastError;

    @TableLogic
    private Byte deleteState;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
