package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 群聊图集图片请求
@Data
public class GroupChatAlbumImageRequest {

    @NotBlank
    private String mediaUrl;

    private String mediaMime;

    private Long mediaSize;

    private Integer mediaWidth;

    private Integer mediaHeight;
}
