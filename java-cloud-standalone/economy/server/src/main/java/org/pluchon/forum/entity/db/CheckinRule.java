package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 签到积分规则表实体类
 */
@Data
@TableName("checkin_rule")
@Schema(description = "签到积分规则实体")
public class CheckinRule {

    @Schema(description = "编号, 主键, 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "月份, 0表示默认规则, 1-12表示具体月份")
    private Byte month;

    @Schema(description = "当月第几天, 1-31")
    private Byte dayNumber;

    @Schema(description = "签到获得积分")
    private Integer points;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
