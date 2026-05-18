package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminAiTrendsVO {

    private AdminAiChartVO day;

    private AdminAiChartVO week;

    private AdminAiChartVO month;
}
