package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 站内信表实体类，对应 message
 */
@Data
@TableName("message")
@Schema(description = "站内信实体")
public class Message {

    @Schema(description = "站内信ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "发送者用户ID", example = "10")
    private Long postUserId;

    @Schema(description = "接收者用户ID", example = "20")
    private Long receiveUserId;

    @Schema(description = "消息类型: 0文本 1图片 2GIF 3语音通话摘要", example = "0")
    private Byte messageType;

    @Schema(description = "文本内容(图片/GIF 消息为 null)", example = "你好，欢迎来到论坛！")
    private String content;

    @Schema(description = "媒体URL(OSS); 文本消息为 null", example = "https://oss/.../xxx.jpg")
    private String mediaUrl;

    @Schema(description = "媒体MIME, 例如 image/jpeg / image/png / image/gif")
    private String mediaMime;

    @Schema(description = "媒体字节大小")
    private Long mediaSize;

    @Schema(description = "媒体像素宽")
    private Integer mediaWidth;

    @Schema(description = "媒体像素高")
    private Integer mediaHeight;

    @Schema(description = "状态: 0未读 1已读 2已撤回", example = "0")
    private Byte state;

    @Schema(description = "是否删除: 0否 1是", example = "0")
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
