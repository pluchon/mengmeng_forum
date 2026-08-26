package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("forum_mascot_model")
public class ForumMascotModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    // 前端静态资源相对路径
    private String modelRelPath;

    private BigDecimal modelScale;

    private Integer posX;

    private Integer posY;

    private Integer stageWidth;

    private Integer stageHeight;

    // 0草稿 1上架 2下架
    private Byte shelfStatus;

    private Integer sortOrder;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
