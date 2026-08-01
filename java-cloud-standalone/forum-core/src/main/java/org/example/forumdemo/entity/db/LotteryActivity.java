package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("lottery_activity")
@Schema(description = "抽奖活动")
public class LotteryActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String coverImageUrl;

    @Schema(description = "创建人(管理员)用户ID")
    private Long publisherId;

    private Integer costPointsPerDraw;

    @Schema(description = "1对用户端开放 0关闭")
    private Byte status;

    @Schema(description = "0筹划中 1进行中 2已截止")
    private Byte phase;

    private Date startTime;

    private Date endTime;

    @JsonIgnore
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
