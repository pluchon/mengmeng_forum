package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作台：当前最新抽奖活动近 24 小时参与次数（按整点桶）。
 */
@Data
public class AdminLotteryTrendVO {

    private Long activityId;

    private String activityTitle;

    private List<String> categories = new ArrayList<>();

    private List<Integer> draws = new ArrayList<>();
}
