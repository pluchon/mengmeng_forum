package org.pluchon.forum.common.enums;

import lombok.Getter;

// 奖品库的状态
@Getter
public enum LotteryPrizeCatalogStatus {

    DRAFT((byte) 0, "草稿"),
    ON_SHELF((byte) 1, "上架"),
    OFF_SHELF((byte) 2, "下架");

    private final byte code;
    private final String label;

    LotteryPrizeCatalogStatus(byte code, String label) {
        this.code = code;
        this.label = label;
    }

    public static boolean isOnShelf(Byte code) {
        return code != null && code == ON_SHELF.code;
    }

    // 反查枚举，后续会用到
    public static LotteryPrizeCatalogStatus fromCode(Byte code) {
        if (code == null) {
            return null;
        }
        for (LotteryPrizeCatalogStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
