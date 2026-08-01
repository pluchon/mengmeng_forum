package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("lottery_prize")
@Schema(description = "抽奖奖品")
public class LotteryPrize {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @Schema(description = "0谢谢 1大奖 2小奖 3安慰奖 4积分 5VIP天")
    private Byte prizeType;

    @Schema(description = "积分额或VIP天数")
    private Integer prizeValue;

    @Schema(description = "库存数量,-1不限量")
    private Integer stockQuantity;

    @Schema(description = "0草稿 1上架 2下架")
    private Byte catalogStatus;

    @Schema(description = "1=神秘大奖子项池")
    private Byte isMysteryBundle;

    @Schema(description = "奖品图相对路径")
    private String imagePath;

    @JsonIgnore
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
