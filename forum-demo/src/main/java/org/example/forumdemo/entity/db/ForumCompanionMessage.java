package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("forum_companion_message")
public class ForumCompanionMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    private String role;

    private String content;

    @TableField("msg_type")
    private String msgType;

    @TableField("image_url")
    private String imageUrl;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;
}
