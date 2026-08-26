package org.pluchon.forum.entity.vo.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 商城列表条目 VO. 列表页只承载基本信息, 表情内单图通过详情接口拉取.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "表情包商品列表条目")
public class EmojiShopListItemVO {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "表情包名称")
    private String name;

    @Schema(description = "表情包分类")
    private String category;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "售价(积分)")
    private Integer price;

    @Schema(description = "销售数量")
    private Integer salesCount;

    @Schema(description = "上传者ID, NULL 表示站长推荐")
    private Long uploadUserId;

    @Schema(description = "上传者昵称(站长推荐为 NULL)")
    private String uploadUserNickname;

    @Schema(description = "上传者头像 URL(站长推荐为 NULL)")
    private String uploadUserAvatarUrl;

    @Schema(description = "当前登录用户是否已购买; 未登录返回 false")
    private Boolean owned;

    @Schema(description = "状态: 0待审核 1上架 2下架 3草稿")
    private Byte status;

    @Schema(description = "创建时间")
    private Date createTime;
}
