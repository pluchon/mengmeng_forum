package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

// 发送群聊图集请求
@Data
public class SendGroupChatAlbumMessageRequest {

    @NotNull
    private Long groupId;

    @Size(max = 500)
    private String content;

    private Long replyMessageId;

    @Valid
    @Size(min = 1, max = 10)
    private List<GroupChatAlbumImageRequest> images;
}
