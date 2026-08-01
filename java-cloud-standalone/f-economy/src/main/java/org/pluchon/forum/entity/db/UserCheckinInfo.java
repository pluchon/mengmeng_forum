package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户签到状态表实体类
 */
@Data
@TableName("user_checkin_info")
@Schema(description = "用户签到状态实体")
public class UserCheckinInfo {

    @Schema(description = "编号, 主键, 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户编号")
    private Long userId;

    @Schema(description = "累计签到天数")
    private Integer totalDays;

    @Schema(description = "当前连续签到天数")
    private Integer streakDays;

    @Schema(description = "签到累计获得积分")
    private Integer totalPoints;

    @Schema(description = "最后一次签到日期")
    private Date lastCheckin;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
