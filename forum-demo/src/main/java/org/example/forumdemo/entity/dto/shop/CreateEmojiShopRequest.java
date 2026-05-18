package org.example.forumdemo.entity.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 创建表情包商品请求.
 * 流程:
 *   1) 前端逐张调 /file/uploadEmojiShopImage 上传封面 + 单图; 服务端返回 OSS URL
 *   2) 把 URL 列表传到此接口创建商品
 *   3) 服务端做包名 + 单图 AI 审核, 通过后落 status=1 上架
 */
@Data
@Schema(description = "创建表情包商品请求")
public class CreateEmojiShopRequest {

    @Schema(description = "表情包名称", example = "萌萌兔表情包")
    private String name;

    @Schema(description = "表情包说明(选填, 最多 100 字)", example = "购买后在私信输入「已购」即可发送")
    private String description;

    @Schema(description = "封面图URL, 必须是 /file/uploadEmojiShopImage 返回的本站URL")
    private String coverUrl;

    @Schema(description = "售价(积分), 0~100000", example = "10")
    private Integer price;

    @Schema(description = "包内表情图片URL列表(已上传到本站), 1~60张")
    private List<String> imageUrls;
}
