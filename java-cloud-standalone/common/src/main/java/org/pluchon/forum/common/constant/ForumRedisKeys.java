package org.pluchon.forum.common.constant;

// 论坛通用 Redis Key / TTL 游戏见 GameRedisKeys，搜索倒排见 SearchRedisKeys
public final class ForumRedisKeys {

    public static final String WS_PUSH_CHANNEL = "forum:ws:push";
    public static final String GAME_ROOM_EVENT_CHANNEL = "forum:game:room:event";

    public static final String JWT_TOKEN_VERSION = "forum:jwt:tv:";
    public static final String USER_INFO = "user_info:";
    public static final String USER_NAME = "user_name:";
    public static final String USER_LIKES = "user_likes:";
    public static final String EMPTY_MARK = "NOT_FOUND";

    public static final long TTL_USER_INFO = 300L;
    public static final long TTL_USER_NAME = 300L;
    public static final long TTL_EMPTY_MARK = 30L;
    public static final long TTL_USER_LIKES = 600L;

    public static final String HOT_ARTICLES = "hot:articles";
    public static final String HOT_ARTICLES_ACTIVE = "hot:articles:active";
    public static final String HOT_ARTICLES_SLOT_A = "hot:articles:a";
    public static final String HOT_ARTICLES_SLOT_B = "hot:articles:b";
    public static final String HOT_ARTICLES_METRIC_BASELINE = "hot:articles:metric:baseline";
    public static final String HOT_ARTICLES_PERIOD_SCORE = "hot:articles:period:score";
    public static final String HOT_ARTICLES_TREND = "hot:articles:trend";
    public static final String HOT_ARTICLES_TREND_INITIALIZED = "hot:articles:trend:initialized";

    // 音乐大厅本周热榜（蓝绿 ZSet，member=musicKey）
    public static final String HOT_MUSIC_ACTIVE = "hot:music:active";
    public static final String HOT_MUSIC_SLOT_A = "hot:music:a";
    public static final String HOT_MUSIC_SLOT_B = "hot:music:b";
    public static final String MQ_EVENT_DEDUP = "forum:mq:dedup:";
    public static final long TTL_MQ_EVENT_DEDUP = 86400L;
    public static final String GAME_MATCH_ROOM = "forum:game:match:";
    public static final String SMS_VERIFY = "v-bind-";
    public static final String SMS_VERIFY_RESET = "v-reset-";
    public static final String SMS_COOLDOWN = "sms_cd:";
    public static final String SMS_COUNT = "sms_count:";
    public static final long TTL_SMS_COUNT = 1800L;

    public static final String MAIL_VERIFY = "m-bind-";
    public static final String MAIL_VERIFY_RESET = "m-reset-";
    public static final String MAIL_COOLDOWN = "mail_cd:";
    public static final String MAIL_COUNT = "mail_count:";
    public static final long TTL_MAIL_COUNT = 1800L;

    public static final String BOARD_LIST = "board_list:";
    public static final long TTL_BOARD_LIST = 3600L;

    public static final String MASCOT_DAILY_CHAT = "mascot:daily:chat:";

    public static final String MESSAGE_SESSIONS = "message_sessions:";
    public static final String MESSAGE_UNREAD_COUNT = "message_unread_count:";
    public static final long TTL_MESSAGE_SESSIONS = 300L;

    public static final String USER_EMOJI_LIST = "user_emoji:";
    public static final long TTL_USER_EMOJI_LIST = 600L;

    public static final String ARTICLE_SUMMARY = "article_summary:";
    public static final long TTL_ARTICLE_SUMMARY = 3600L;

    public static final String DANMAKU_RATE = "forum:danmaku:rate:";
    public static final long TTL_DANMAKU_RATE = 2L;
    public static final String DANMAKU_DUP = "forum:danmaku:dup:";
    public static final long TTL_DANMAKU_DUP = 10L;
    public static final String DANMAKU_MINUTE = "forum:danmaku:minute:";
    public static final long TTL_DANMAKU_MINUTE = 60L;

    public static final String ARTICLE_GUIDE = "article_guide:";
    public static final long TTL_ARTICLE_GUIDE = 604800L;

    public static final String CHECKIN_STATUS = "checkin:status:";
    public static final long TTL_CHECKIN_STATUS = 600L;
    public static final String CHECKIN_RULE = "checkin:rule:m";
    public static final long TTL_CHECKIN_RULE = 21600L;

    public static final String SHOP_DETAIL = "shop:detail:";
    public static final String SHOP_DETAIL_VERSION = "shop:detail:version:";
    public static final String SHOP_DETAIL_LOCK = "shop:detail:lock:";
    public static final long TTL_SHOP_DETAIL = 1800L;
    public static final long TTL_SHOP_DETAIL_LOCK = 3L;
    public static final String SHOP_LIST = "shop:list:";
    public static final String SHOP_LIST_VERSION = "shop:list:version";
    public static final String SHOP_LIST_LOCK = "shop:list:lock:";
    public static final long TTL_SHOP_LIST = 300L;
    public static final long TTL_SHOP_LIST_LOCK = 3L;

    public static final String AUDIT_RESULT_DEDUP = "ai_audit:result_dedup:";
    public static final long TTL_AUDIT_RESULT_DEDUP = 86400L;
    public static final String AUDIT_TASK_STATE = "ai_audit:task_state:";

    public static final String LOTTERY_PUBLIC_RECENT = "lottery:public:recent:";
    public static final long TTL_LOTTERY_PUBLIC_RECENT = 20L;

    // 密码登录失败计数 后缀为账号指纹
    public static final String LOGIN_FAIL = "forum:login:fail:";
    public static final long TTL_LOGIN_FAIL = 900L;

    private ForumRedisKeys() {
    }
}
