package org.pluchon.forum.entity.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 保存表情包草稿请求。草稿允许暂不填写完整商品信息
@Data
@Schema(description = "保存表情包草稿请求")
public class SaveEmojiShopDraftRequest {

    @Schema(description = "草稿商品ID，首次保存不传")
    private Long draftId;

    @Schema(description = "表情包名称")
    private String name;

    @Schema(description = "表情包说明")
    private String description;

    @Schema(description = "表情包分类: MOE/ONEE_SAN/REPOST/MEME/OTHER")
    private String category;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "售价积分")
    private Integer price;

    @Schema(description = "包内表情图片URL列表，最多60张")
    private List<String> imageUrls;
}
