package org.example.forumdemo.entity.vo.lottery;

import lombok.Data;

import java.util.Date;

@Data
public class LotteryHourStatRow {

    private Date statHour;

    private Integer drawCount;
}
