package org.example.forumdemo.entity.vo.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 商品详情 VO. 包含包内所有单图(按 sort 升序).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "表情包商品详情")
public class EmojiShopDetailVO {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "表情包名称")
    private String name;

    @Schema(description = "表情包说明")
    private String description;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "售价(积分)")
    private Integer price;

    @Schema(description = "销售数量")
    private Integer salesCount;

    @Schema(description = "状态: 0待审核 1上架 2下架")
    private Byte status;

    @Schema(description = "上传者ID, NULL 表示站长推荐")
    private Long uploadUserId;

    @Schema(description = "上传者昵称")
    private String uploadUserNickname;

    @Schema(description = "上传者头像 URL")
    private String uploadUserAvatarUrl;

    @Schema(description = "上传者 VIP 等级: 0 普通 1 PRO 2 MAX")
    private Byte uploadUserVipTier;

    @Schema(description = "上传者 VIP 到期时间")
    private Date uploadUserVipExpireAt;

    @Schema(description = "包内表情图URL列表(按 sort 升序)")
    private List<String> imageUrls;

    @Schema(description = "当前登录用户是否已购买; 未登录返回 false")
    private Boolean owned;

    @Schema(description = "创建时间")
    private Date createTime;
}
