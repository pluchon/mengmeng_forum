package org.pluchon.forum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// ai 服务侧的向量相关度阈值，与 ai-server（Python）config.yaml 的 rag 段一一对应。
// 两侧是各自独立生效的两份配置，注释曾声称“已对齐”但实际出现过漂移
// （articleSearchMinScore 0.20 vs rag.vector_min_score 0.22），
// 因此调参时必须同时改两边，或以本文件为准再回写 Python 配置。
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "forum.ai.rag")
public class ForumAiRagProperties {

    // 站内搜索：帖子向量最低相关度，对应 Python rag.vector_min_score
    private double articleSearchMinScore = 0.20;

    // 看板娘引用：向量相关度阈值，对应 Python rag.mascot_min_score
    private double mascotMinScore = 0.42;

    // 看板娘引用：最高分不足则整批不展示，避免泛语义误召回，对应 rag.mascot_top_min_to_show
    private double mascotTopMinToShow = 0.46;

    // 看板娘引用：高于此分可免标题关键词校验
    private double mascotHighScoreBypass = 0.52;
}
