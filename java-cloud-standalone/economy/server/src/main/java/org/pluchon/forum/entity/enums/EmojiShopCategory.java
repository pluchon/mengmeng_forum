package org.pluchon.forum.entity.enums;

import lombok.Getter;

import java.util.Locale;

// 表情商城分类
@Getter
public enum EmojiShopCategory {

    MOE("MOE", "萌系"),
    ONEE_SAN("ONEE_SAN", "御姐"),
    REPOST("REPOST", "搬运"),
    MEME("MEME", "网络热梗"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    EmojiShopCategory(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static EmojiShopCategory fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (EmojiShopCategory value : values()) {
            if (value.code.equals(normalized)) {
                return value;
            }
        }
        return null;
    }

}
