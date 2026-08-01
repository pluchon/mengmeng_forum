package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 系统消息实体, 对应 system_message 表.
 * 用于平台 -> 用户的单向通知(审核结果 / 公告 / 封禁提示等),
 * 与用户间私信 message 表物理隔离, 互不污染索引与展示逻辑.
 */
@Data
@TableName("system_message")
@Schema(description = "系统消息实体(审核结果/公告等)")
public class SystemMessage {

    @Schema(description = "系统消息ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "接收者用户ID", example = "20")
    private Long receiveUserId;

    @Schema(description = "系统消息类型: 1审核通过 2审核未通过 3审核异常 99公告(预留)", example = "1")
    private Byte type;

    @Schema(description = "消息标题(展示用)")
    private String title;

    @Schema(description = "消息正文(展示用, 最长500字)")
    private String content;

    @Schema(description = "关联业务ID(如审核类: articleId)")
    private Long relatedId;

    @Schema(description = "附加结构化数据(JSON字符串), 可为空")
    private String payload;

    @Schema(description = "状态: 0未读 1已读", example = "0")
    private Byte state;

    @Schema(description = "是否删除: 0否 1是", example = "0")
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
