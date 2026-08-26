package org.pluchon.forum.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "陪伴助手消息")
public class CompanionMessageVO {

    private Long id;
    private String role;
    private String content;
    private String type;
    private String url;
    // 联网检索配图 text 消息时由 image_url 列承载
    private String searchImageUrl;
    // 联网检索图集；仅在模型判断图片有助于理解时返回
    private java.util.List<CompanionImageGalleryItemVO> imageGallery;
    private Date at;
}
