package org.pluchon.forum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// 音乐氛围标签的唯一来源，前端筛选栏/投稿快选与 ai-server 审核提示词都从这里取，
// 避免各端各持一份词表而漂移。
// 注意这是候选集而非白名单：AI 审核允许补充新标签，用户上传也可自由输入，
// 因此入库侧不按此列表拦截，只用它做展示与提示。
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "forum.music")
public class ForumMusicProperties {

    // 「热门」是默认态而非真实氛围，曲库筛选时被当作不过滤处理，
    // 详见 ArticleMusicCatalogServiceImpl 的 filterMood 判断。
    private static final List<String> DEFAULT_MOOD_TAGS = List.of(
            "热门", "治愈", "清新", "浪漫", "轻松", "深夜", "轻音乐", "适合配图");

    // 氛围标签候选集，配置缺失时回退到内置默认值
    private List<String> moodTags = new ArrayList<>(DEFAULT_MOOD_TAGS);

    // 去重且剔除空白后的有序标签列表
    public List<String> resolvedMoodTags() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : moodTags == null ? List.<String>of() : moodTags) {
            if (tag != null && !tag.isBlank()) {
                result.add(tag.trim());
            }
        }
        if (result.isEmpty()) {
            return List.copyOf(DEFAULT_MOOD_TAGS);
        }
        return List.copyOf(result);
    }
}
