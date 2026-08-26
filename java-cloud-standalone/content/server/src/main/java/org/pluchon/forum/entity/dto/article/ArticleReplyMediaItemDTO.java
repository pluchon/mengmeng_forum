package org.pluchon.forum.entity.dto.article;

import lombok.Data;

@Data
public class ArticleReplyMediaItemDTO {
    // 1 用户图片 2 商城表情
    private Byte mediaType;
    private String mediaUrl;
    // 商城表情必填
    private Long shopId;
}
