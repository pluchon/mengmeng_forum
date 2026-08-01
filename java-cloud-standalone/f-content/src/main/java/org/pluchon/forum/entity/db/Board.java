package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 版块表实体类，对应 board
 */
@Data
@TableName("board")
@Schema(description = "版块实体")
public class Board {

    @Schema(description = "版块ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "版块名称", example = "Java")
    private String name;

    @Schema(description = "所属分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "帖子数量", example = "128")
    private Integer articleCount;

    @Schema(description = "排序优先级，数字越小越靠前", example = "1")
    @JsonIgnore
    private Integer sort;

    @Schema(description = "状态: 0正常 1禁用", example = "0")
    private Byte state;

    @Schema(description = "是否删除: 0否 1是", example = "0")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
