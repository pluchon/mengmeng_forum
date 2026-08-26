package org.pluchon.forum.entity.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 创建表情包商品请求
@Data
@Schema(description = "创建表情包商品请求")
public class CreateEmojiShopRequest {

    // 表情包名称
    @Schema(description = "表情包名称", example = "萌萌兔表情包")
    private String name;

    // 表情包说明
    @Schema(description = "表情包说明", example = "购买后在私信输入已购即可发送")
    private String description;

    // 表情包分类
    @Schema(description = "表情包分类", example = "MOE")
    private String category;

    // 封面图链接
    @Schema(description = "封面图链接")
    private String coverUrl;

    // 售价积分
    @Schema(description = "售价积分", example = "10")
    private Integer price;

    // 表情图片链接列表
    @Schema(description = "表情图片链接列表")
    private List<String> imageUrls;
}
