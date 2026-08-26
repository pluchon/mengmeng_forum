package org.pluchon.forum.entity.vo.message;

import lombok.Data;

// 私信图集图片展示数据
@Data
public class MessageAlbumImageVO {

    private Long id;

    private String mediaUrl;

    private String mediaMime;

    private Long mediaSize;

    private Integer mediaWidth;

    private Integer mediaHeight;

    private Integer sortOrder;
}
