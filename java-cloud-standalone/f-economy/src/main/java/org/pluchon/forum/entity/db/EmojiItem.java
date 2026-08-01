package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 表情包图片表实体, 对应 emoji_item. 单图 delete_state 字段保留, 当前版本不开放增删图接口.
 */
@Data
@TableName("emoji_item")
@Schema(description = "表情包图片实体")
public class EmojiItem {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属商品ID")
    private Long shopId;

    @Schema(description = "表情图片URL")
    private String imageUrl;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
