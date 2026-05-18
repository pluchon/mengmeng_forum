package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

/**
 * 工作台 AI 调用趋势：文本模型与生图模型分开展示。
 */
@Data
public class AdminAiTrendsBundleVO {

    private AdminAiTrendsVO text = new AdminAiTrendsVO();

    private AdminAiTrendsVO image = new AdminAiTrendsVO();
}
