package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("category")
@Schema(description = "分类实体")
public class Category {

    @Schema(description = "分类ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "分类名称", example = "代码世界")
    private String name;

    @Schema(description = "分类描述")
    private String description;

    @Schema(description = "排序优先级", example = "1")
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
