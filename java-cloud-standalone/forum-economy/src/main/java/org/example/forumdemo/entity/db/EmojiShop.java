package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 表情包商品表实体, 对应 emoji_shop.
 * uploadUserId 为 null 表示站长推荐, 非空表示用户上传.
 */
@Data
@TableName("emoji_shop")
@Schema(description = "表情包商品实体")
public class EmojiShop {

    @Schema(description = "商品ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "表情包名称")
    private String name;

    @Schema(description = "表情包说明(上传者填写)")
    private String description;

    @Schema(description = "封面预览图URL")
    private String coverUrl;

    @Schema(description = "售价(积分)")
    private Integer price;

    @Schema(description = "上传者用户ID, NULL 表示站长推荐")
    private Long uploadUserId;

    @Schema(description = "销售数量")
    private Integer salesCount;

    @Schema(description = "状态: 0待审核 1上架 2下架")
    private Byte status;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
