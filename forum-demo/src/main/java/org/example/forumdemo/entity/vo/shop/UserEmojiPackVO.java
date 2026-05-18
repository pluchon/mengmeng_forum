package org.example.forumdemo.entity.vo.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 用户已购表情包 VO. 在聊天面板"我的已购"选项卡里显示, 一次性返回每个包内所有 URL.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户已购表情包")
public class UserEmojiPackVO {

    @Schema(description = "已购记录ID")
    private Long userEmojiId;

    @Schema(description = "商品ID")
    private Long shopId;

    @Schema(description = "表情包名称")
    private String name;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "支付积分")
    private Integer pricePaid;

    @Schema(description = "包内表情图URL列表(按 sort 升序)")
    private List<String> imageUrls;

    @Schema(description = "购买时间")
    private Date createTime;
}
