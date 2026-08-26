package org.pluchon.forum.entity.vo.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

// 我的表情包草稿详情，用于继续编辑
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "表情包草稿详情")
public class EmojiShopDraftVO {

    @Schema(description = "草稿商品ID")
    private Long id;

    @Schema(description = "表情包名称")
    private String name;

    @Schema(description = "表情包说明")
    private String description;

    @Schema(description = "表情包分类")
    private String category;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "售价积分")
    private Integer price;

    @Schema(description = "包内表情图片")
    private List<String> imageUrls;

    @Schema(description = "最后编辑时间")
    private Date updateTime;
}
