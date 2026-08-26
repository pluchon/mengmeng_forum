package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 用户已购表情包表实体, 对应 user_emoji. user_id, shop_id 全表唯一.
@Data
@TableName("user_emoji")
@Schema(description = "用户已购表情包实体")
public class UserEmoji {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "买家用户ID")
    private Long userId;

    @Schema(description = "购买的商品ID")
    private Long shopId;

    @Schema(description = "实际支付积分")
    private Integer pricePaid;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "购买时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
