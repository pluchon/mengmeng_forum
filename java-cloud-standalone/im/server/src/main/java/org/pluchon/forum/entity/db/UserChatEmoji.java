package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 用户聊天表情收藏表实体, 对应 user_chat_emoji 来源 1: 用户主动上传 走 /file/uploadChatEmoji 来源 2: 收藏私信里的图片 origin_message_id 非空 来源 3: 收藏群聊里的图片 origin_group_message_id 非空; 后两者 media_url 直接复用消息 URL
@Data
@TableName("user_chat_emoji")
@Schema(description = "用户聊天表情收藏实体")
public class UserChatEmoji {

    @Schema(description = "主键, 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属用户ID")
    private Long userId;

    @Schema(description = "表情图URL(OSS)")
    private String mediaUrl;

    @Schema(description = "类型: 0静态图 1GIF", example = "0")
    private Byte mediaType;

    @Schema(description = "MIME, 例如 image/jpeg / image/png / image/gif")
    private String mediaMime;

    @Schema(description = "字节大小")
    private Long mediaSize;

    @Schema(description = "来源私信消息ID(从私信聊天图片收藏时回填)")
    private Long originMessageId;

    @Schema(description = "来源群聊消息ID(从群聊图片收藏时回填); 与 originMessageId 互斥")
    private Long originGroupMessageId;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
