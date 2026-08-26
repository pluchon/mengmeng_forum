package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 群聊图集图片
@Data
@TableName("group_chat_message_album_image")
public class GroupChatMessageAlbumImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private String mediaUrl;

    private String mediaMime;

    private Long mediaSize;

    private Integer mediaWidth;

    private Integer mediaHeight;

    private Integer sortOrder;

    private Date createTime;

    private Date updateTime;

    private Byte deleteState;
}
