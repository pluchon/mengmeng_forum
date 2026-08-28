package org.pluchon.forum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// 站内搜索与推荐的相关度阈值。这些值需要按召回效果反复调参，
// 放在配置里可以只改 Nacos 并重启，不必重新编译发布 content 服务。
// 注意：ai-server 的 config.yaml 里有一组同类阈值，两边是各自独立生效的，
// 调参时需要一起看，避免只改一侧造成召回口径不一致。
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "forum.search")
public class ForumSearchProperties {

    // AI 搜索：候选集 hybrid_rank 最低分（社区与创作中心共用）
    private double articleHybridMinScore = 0.22;

    // AI 搜索：无字面候选时，纯向量兜底最低分。更高以减少噪声 query 误召回
    private double articleVectorMinScore = 0.36;

    // 帖子正文未命中时，作者语义回退的高相似度阈值
    private double articleAuthorVectorMinScore = 0.72;

    // AI 用户搜索：纯向量兜底最低分
    private double userVectorMinScore = 0.38;

    // 推荐流：向量候选的最低相似度
    private double recommendVectorMinScore = 0.30;
}
