package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("forum_ai_model_price")
public class ForumAiModelPrice {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("model_code")
    private String modelCode;

    private String provider;

    @TableField("bill_unit")
    private String billUnit;

    @TableField("price_yuan")
    private BigDecimal priceYuan;

    private Byte enabled;

    private String remark;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
