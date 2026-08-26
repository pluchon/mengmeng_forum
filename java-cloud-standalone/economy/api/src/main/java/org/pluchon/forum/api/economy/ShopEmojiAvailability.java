package org.pluchon.forum.api.economy;

// 商城表情在当前时刻的可用状态
public enum ShopEmojiAvailability {
    AVAILABLE,
    SERIES_OFFLINE,
    SERIES_DELETED,
    ITEM_DELETED,
    NOT_OWNED,
    NOT_FOUND
}
