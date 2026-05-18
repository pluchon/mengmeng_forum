package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.Date;

/** Mapper 聚合查询中间结果 */
@Data
public class LotteryDrawUserAggRow {

    private Long userId;

    private Date lastDrawTime;

    private Integer drawCount;
}
