package org.pluchon.forum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 看板娘“复杂请求”判定参数。命中即把 VIP Pro 的会话升级到更贵的深度模型，
// 直接决定 AI 调用成本，因此做成可配置：调整投放策略不必重新编译发布 ai 服务。
// 这是一个廉价的字面启发式前置筛，与 Python 侧的工具意图路由职责不同，两者不重叠。
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "forum.mascot.complexity")
public class ForumMascotComplexityProperties {

    private static final List<String> DEFAULT_KEYWORDS = List.of(
            "深入分析", "详细分析", "对比", "比较", "方案", "规划", "计划",
            "推理", "论证", "优缺点", "多步", "教程", "长文", "大纲");

    private static final List<String> DEFAULT_STRONG_KEYWORDS = List.of("帮我写一篇", "制定一个");

    // 单条消息长度达到该值直接判定为复杂
    private int directLengthThreshold = 320;

    // 复杂度指示词，命中数量达到 minIndicators 即判定为复杂
    private List<String> keywords = new ArrayList<>(DEFAULT_KEYWORDS);

    // 命中任意一个即直接判定为复杂的强信号词
    private List<String> strongKeywords = new ArrayList<>(DEFAULT_STRONG_KEYWORDS);

    // 判定为复杂所需的指示词命中数
    private int minIndicators = 2;

    // 多轮对话中的宽松判定：轮次与长度同时达标时，命中 1 个指示词即算复杂
    private int historyTurnsThreshold = 4;

    private int historyLengthThreshold = 120;

    public List<String> resolvedKeywords() {
        return keywords == null || keywords.isEmpty() ? DEFAULT_KEYWORDS : keywords;
    }

    public List<String> resolvedStrongKeywords() {
        return strongKeywords == null || strongKeywords.isEmpty() ? DEFAULT_STRONG_KEYWORDS : strongKeywords;
    }
}
