package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 抽奖请求幂等表，防止重复扣积分与重复发奖
@Data
@TableName("lottery_draw_request")
public class LotteryDrawRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private String requestId;

    private Integer times;

    private String batchKey;

    private Integer pityAfter;

    // 本批使用抵扣券数量；NULL 表示历史记录未保存
    private Integer vouchersUsed;

    // 本批实际扣除萌币；NULL 表示历史记录未保存
    private Integer pointsCharged;

    @TableLogic
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
