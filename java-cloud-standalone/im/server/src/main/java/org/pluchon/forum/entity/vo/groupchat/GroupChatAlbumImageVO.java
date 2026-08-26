package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;

// 群聊图集图片展示数据
@Data
public class GroupChatAlbumImageVO {

    private Long id;

    private String mediaUrl;

    private String mediaMime;

    private Long mediaSize;

    private Integer mediaWidth;

    private Integer mediaHeight;

    private Integer sortOrder;
}
