package org.pluchon.forum.common;

import java.util.Arrays;

// 用户主动AI能力收费白名单，未列出的功能一律免费
public enum BillableFeature {
    AI_POLISH("ai_polish", true),
    ARTICLE_TAG_RECOMMEND("article_tag_recommend", true),
    ARTICLE_COVER("article_cover", true),
    CREATOR_INSIGHT("creator_insight", true),
    COMPANION_CHAT("companion_chat", true),
    COMPANION_WRITING("companion_writing", true),
    COMPANION_HELP("companion_help", true),
    COMPANION_IMAGE("companion_image", true),
    COMPANION_CONTEXT_COMPRESS("companion_context_compress", true);

    private final String code;
    private final boolean qwenQuota;

    BillableFeature(String code, boolean qwenQuota) {
        this.code = code;
        this.qwenQuota = qwenQuota;
    }

    public static boolean contains(String featureCode) {
        return featureCode != null && Arrays.stream(values())
                .anyMatch(item -> item.code.equals(featureCode.trim()));
    }

    public static boolean usesQwenQuota(String featureCode) {
        return featureCode != null && Arrays.stream(values())
                .anyMatch(item -> item.code.equals(featureCode.trim()) && item.qwenQuota);
    }
}
