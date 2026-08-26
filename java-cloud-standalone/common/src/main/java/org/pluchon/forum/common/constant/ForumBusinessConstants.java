package org.pluchon.forum.common.constant;

import java.util.Set;

// 论坛业务阈值、状态码、积分与 AI 模型等业务常量 非 Redis Key
public final class ForumBusinessConstants {

    public static final Byte DELETE_STATE_TRUE = 1;
    public static final Byte DELETE_STATE_FALSE = 0;
    public static final Byte STATE_BANNED = 1;
    public static final Byte CREATOR_STATE_CERTIFIED = 1;

    public static final Byte MESSAGE_STATE_UNREAD = 0;
    public static final Byte MESSAGE_STATE_READ = 1;
    public static final Byte MESSAGE_STATE_RECALLED = 2;
    // 文本内容审核未通过（仅发送方可见，气泡左侧红叹号）
    public static final Byte MESSAGE_STATE_AUDIT_FAILED = 3;
    public static final long MESSAGE_RECALL_WINDOW_SECONDS = 120L;

    public static final Byte MESSAGE_TYPE_TEXT = 0;
    public static final Byte MESSAGE_TYPE_IMAGE = 1;
    public static final Byte MESSAGE_TYPE_GIF = 2;
    public static final Byte MESSAGE_TYPE_ALBUM = 4;
    public static final int MESSAGE_ALBUM_MAX_IMAGES = 10;
    public static final Byte EMOJI_MEDIA_TYPE_IMAGE = 0;
    public static final Byte EMOJI_MEDIA_TYPE_GIF = 1;
    public static final int EMOJI_MAX_PER_USER = 200;

    public static final Byte REPLY_MEDIA_TYPE_IMAGE = 1;
    public static final Byte REPLY_MEDIA_TYPE_SHOP_EMOJI = 2;
    // 评论/楼中楼：图片 + 表情包合计上限 与前端一行 8 槽位对齐
    public static final int REPLY_MEDIA_MAX_TOTAL = 8;
    // 兼容旧引用：单类上限不再单独卡死，以合计为准
    public static final int REPLY_MEDIA_MAX_IMAGES = REPLY_MEDIA_MAX_TOTAL;
    public static final int REPLY_MEDIA_MAX_EMOJIS = REPLY_MEDIA_MAX_TOTAL;

    public static final double HOT_SCORE_WEIGHT_LIKE = 1.0;
    public static final double HOT_SCORE_WEIGHT_VISIT = 1.0;
    public static final double HOT_SCORE_WEIGHT_FAVORITE = 0.7;
    public static final double HOT_SCORE_WEIGHT_REPLY = 0.5;

    // 热帖榜只纳入最近 N 天发布的帖子
    public static final int HOT_RANK_WINDOW_DAYS = 7;
    // 24 小时内新帖扶持系数
    public static final double HOT_RANK_NEW_POST_BOOST = 1.5;
    public static final int HOT_RANK_NEW_POST_HOURS = 24;
    // 热帖榜展示：最多 28 条，分页每页最多 14 条（共 2 页）
    public static final int HOT_RANK_LIST_MAX = 28;
    public static final int HOT_RANK_PAGE_SIZE_MAX = 14;

    // 音乐大厅本周热榜：对齐热帖思路的缩水公式
    public static final double MUSIC_HOT_SCORE_WEIGHT_PLAY = 1.0;
    public static final double MUSIC_HOT_SCORE_WEIGHT_FAVORITE = 0.7;
    // 衰减分母：score *= 1/(1 + ageDays / DECAY_DAYS)
    public static final double MUSIC_HOT_SCORE_DECAY_DAYS = 7.0;
    public static final double MUSIC_HOT_NEW_TRACK_BOOST = 1.3;
    public static final int MUSIC_HOT_NEW_TRACK_HOURS = 24;
    // 同一上传者进榜上限，避免公共榜被一人占满
    public static final int MUSIC_HOT_AUTHOR_MAX_PER_LIST = 2;
    // 搜索关键词最大长度
    public static final int SEARCH_KEYWORD_MAX_LEN = 100;

    public static final int SMS_MAX_COUNT = 10;
    public static final int MAIL_MAX_COUNT = 10;
    // 密码登录：同账号连续失败次数上限，窗口见 ForumRedisKeys.TTL_LOGIN_FAIL
    public static final int LOGIN_FAIL_MAX = 5;

    // 账号安全评估：登录日志轻量风险阈值
    public static final int SECURITY_LOGIN_SAMPLE_LIMIT = 30;
    public static final int SECURITY_LOGIN_WINDOW_DAYS = 7;
    public static final int SECURITY_DISTINCT_IP_RISK = 4;
    public static final int SECURITY_DISTINCT_DEVICE_RISK = 4;
    public static final int SECURITY_FAIL_WINDOW_HOURS = 24;
    public static final int SECURITY_FAIL_COUNT_RISK = 5;
    public static final int SECURITY_REGION_HISTORY_MIN = 3;

    public static final int SEARCH_RAG_CANDIDATE_LIMIT = 80;
    public static final int SEARCH_INVERTED_MAX_RESULTS = 120;
    public static final int SEARCH_RAG_MAX_RESULTS = 50;
    public static final String SEARCH_SOURCE_DB = "db";
    public static final String SEARCH_SOURCE_INV = "inv";
    public static final String SEARCH_SOURCE_RAG = "rag";
    public static final String SEARCH_SOURCE_EMPTY = "empty";

    public static final int ARTICLE_GALLERY_MAX = 15;
    public static final int ARTICLE_GALLERY_MIN_CONTENT_LEN = 10;

    public static final long OSS_MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    public static final long IMAGE_HARD_MAX_SIZE = 30L * 1024 * 1024;
    public static final long IMAGE_GIF_MAX_SIZE = 15L * 1024 * 1024;
    public static final long IMAGE_COMPRESS_TARGET_SIZE = (long) (4.8 * 1024 * 1024);
    public static final long IMAGE_COMPRESS_MAX_OUTPUT_SIZE = OSS_MAX_IMAGE_SIZE;
    public static final int IMAGE_COMPRESS_MAX_DIMENSION = 2560;
    // content→forum-ai 审图 JSON（Base64）在 Tomcat 默认 2MB 下会被截断；审图专用压缩阈值
    public static final long IMAGE_AUDIT_JSON_SAFE_RAW_BYTES = 1_400_000L;
    public static final long IMAGE_AUDIT_COMPRESS_TARGET_BYTES = 800_000L;
    public static final int IMAGE_AUDIT_COMPRESS_MAX_DIMENSION = 1920;
    public static final Set<String> IMAGE_SUPPORTED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif");
    public static final String IMAGE_TYPE_GIF = "image/gif";

    public static final long MUSIC_AUDIO_MAX_SIZE = 50L * 1024 * 1024;
    public static final long MUSIC_LRC_MAX_SIZE = 1L * 1024 * 1024;
    public static final int MUSIC_TITLE_MAX_LEN = 100;
    public static final int MUSIC_LYRIC_TEXT_MAX_LEN = 100_000;
    public static final byte USER_MUSIC_STATUS_DRAFT = 0;
    public static final byte USER_MUSIC_STATUS_REVIEWING = 1;
    public static final byte USER_MUSIC_STATUS_PUBLISHED = 2;
    public static final byte USER_MUSIC_STATUS_REJECTED = 3;
    public static final Set<String> MUSIC_AUDIO_EXT = Set.of("mp3", "wav", "flac", "m4a");
    public static final Set<String> MUSIC_AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/flac",
            "audio/mp4", "audio/x-m4a", "application/octet-stream");
    public static final int MUSIC_CATALOG_PAGE_SIZE = 10;

    public static final int DANMAKU_MAX_PER_MINUTE = 20;
    public static final int DANMAKU_QUERY_MAX_WINDOW_MS = 120_000;
    public static final int DANMAKU_MAX_CONTENT_LEN = 30;

    public static final String SUMMARY_ARTICLE_NOT_FOUND = "帖子不存在或已被删除。";
    public static final String SUMMARY_ARTICLE_TOO_SHORT = "当前帖子内容较少（共 %d 字），建议包含更多内容后再尝试 AI 智能总结。";
    public static final String SUMMARY_AI_SERVICE_UNAVAILABLE = "AI 摘要生成暂时不可用，请稍后再试。";

    public static final Byte SHOP_STATUS_PENDING = 0;
    public static final Byte SHOP_STATUS_ONLINE = 1;
    public static final Byte SHOP_STATUS_OFFLINE = 2;
    public static final Byte SHOP_STATUS_DRAFT = 3;

    public static final Byte POINTS_SOURCE_CHECKIN_BASIC = 0;
    public static final Byte POINTS_SOURCE_CHECKIN_BONUS = 1;
    public static final Byte POINTS_SOURCE_SHOP_PURCHASE = 2;
    public static final Byte POINTS_SOURCE_REFUND = 3;
    public static final Byte POINTS_SOURCE_LOTTERY_COST = 4;
    public static final Byte POINTS_SOURCE_LOTTERY_WIN = 5;
    public static final Byte POINTS_SOURCE_REGISTER_BONUS = 6;
    public static final Byte POINTS_SOURCE_VIP_SUBSCRIBE = 7;
    public static final Byte POINTS_SOURCE_AI_COMPANION = 9;
    public static final Byte POINTS_SOURCE_AI_IMAGE = 10;
    public static final Byte POINTS_SOURCE_GAME_WIN = 11;
    public static final Byte POINTS_SOURCE_GAME_LOSE = 12;
    public static final Byte POINTS_SOURCE_TETRIS = 13;
    public static final Byte POINTS_SOURCE_CHECKIN_SURPRISE = 14;
    public static final Byte POINTS_SOURCE_MILESTONE_REWARD = 15;
    public static final Byte POINTS_SOURCE_ADMIN_ADJUST = 99;

    public static final int AI_ESTIMATE_CHAT_INPUT_TOKENS = 2000;
    public static final int AI_ESTIMATE_CHAT_OUTPUT_TOKENS = 1000;

    public static final String AI_MODEL_IMAGE_NORMAL = "wan2.7-image";
    public static final String AI_MODEL_IMAGE_DASH_PREMIUM = "wan2.7-image-pro";
    public static final String AI_MODEL_QWEN_FLASH = "qwen3.7-flash";
    public static final String AI_MODEL_QWEN_DEEP = "qwen3.7-max";

    public static final int POINTS_REGISTER_BONUS_AMOUNT = 1000;
    public static final Byte VIP_TIER_FREE = 0;
    public static final Byte VIP_TIER_PRO = 1;
    public static final Byte VIP_TIER_MAX = 2;
    public static final int GROUP_CHAT_CREATE_LIMIT_FREE = 3;
    public static final int GROUP_CHAT_CREATE_LIMIT_PRO = 10;
    public static final int GROUP_CHAT_CREATE_LIMIT_MAX = 30;
    public static final int GROUP_CHAT_MEMBER_LIMIT_FREE = 100;
    public static final int GROUP_CHAT_MEMBER_LIMIT_PRO = 200;
    public static final int GROUP_CHAT_MEMBER_LIMIT_MAX = 350;
    public static final int GROUP_CHAT_NAME_MAX_LEN = 10;
    public static final int GROUP_CHAT_INTRO_MAX_LEN = 120;
    public static final int GROUP_CHAT_MESSAGE_MAX_LEN = 500;

    public static final Byte LOTTERY_PRIZE_THANKS = 0;
    public static final Byte LOTTERY_PRIZE_GRAND = 1;
    public static final Byte LOTTERY_PRIZE_SMALL = 2;
    public static final Byte LOTTERY_PRIZE_CONSOLATION = 3;
    public static final Byte LOTTERY_PRIZE_POINTS = 4;
    public static final Byte LOTTERY_PRIZE_VIP_DAYS = 5;
    public static final int LOTTERY_PRIZE_SINGLE_POINTS_MAX = 100;
    // prize_value < 0 表示按区间随机积分；当前约定 -1 => 10~50
    public static final int LOTTERY_RANDOM_POINTS_MARKER = -1;
    public static final int LOTTERY_RANDOM_POINTS_MIN = 10;
    public static final int LOTTERY_RANDOM_POINTS_MAX = 50;
    public static final int LOTTERY_HARD_PITY_AFTER_MISSES = 50;

    // 抽奖抵扣券流水来源：1任务发放 2抽奖抵扣 3收集册 4萌星辉商城 5签到惊喜
    public static final Byte LOTTERY_VOUCHER_SOURCE_TASK = 1;
    public static final Byte LOTTERY_VOUCHER_SOURCE_DRAW = 2;
    public static final Byte LOTTERY_VOUCHER_SOURCE_COLLECT = 3;
    public static final Byte LOTTERY_VOUCHER_SOURCE_STARLIGHT = 4;
    public static final Byte LOTTERY_VOUCHER_SOURCE_CHECKIN = 5;

    public static final String LOTTERY_TASK_COMMENT_1 = "COMMENT_1";
    public static final String LOTTERY_TASK_LIKE_3 = "LIKE_3";
    public static final String LOTTERY_TASK_CHECKIN_TODAY = "CHECKIN_TODAY";
    public static final String LOTTERY_TASK_STATUS_LOCKED = "LOCKED";
    public static final String LOTTERY_TASK_STATUS_CLAIMABLE = "CLAIMABLE";
    public static final String LOTTERY_TASK_STATUS_CLAIMED = "CLAIMED";

    public static final int LOTTERY_COLLECT_TOTAL_ICONS = 80;
    public static final int LOTTERY_COLLECT_TEN_UNLOCK_MIN = 1;
    public static final int LOTTERY_COLLECT_TEN_UNLOCK_MAX = 3;
    public static final String LOTTERY_COLLECT_REWARD_RANDOM = "RANDOM";
    public static final String LOTTERY_COLLECT_REWARD_VOUCHER = "VOUCHER";
    public static final String LOTTERY_COLLECT_REWARD_POINTS = "POINTS";
    public static final String LOTTERY_COLLECT_REWARD_VIP_DAYS = "VIP_DAYS";
    public static final Byte POINTS_SOURCE_LOTTERY_COLLECT = 16;

    public static final int EMOJI_SHOP_ITEM_MAX = 60;
    public static final int EMOJI_SHOP_PRICE_MIN = 0;
    public static final int EMOJI_SHOP_PRICE_MAX = 100_000;

    public static final int ARTICLE_AUDIT_MAX_RETRY = 3;
    public static final long ARTICLE_AUDIT_TIMEOUT_SECONDS = 600L;

    public static final String SYSTEM_MSG_TITLE_AUDIT_PASS = "帖子审核通过";
    public static final String SYSTEM_MSG_TITLE_AUDIT_FAIL = "帖子审核未通过";
    public static final String SYSTEM_MSG_TITLE_AUDIT_ERROR = "帖子审核异常";
    public static final String SYSTEM_MSG_TITLE_MUSIC_AUDIT_PASS = "歌曲审核通过";
    public static final String SYSTEM_MSG_TITLE_MUSIC_AUDIT_FAIL = "歌曲审核未通过";
    public static final String SYSTEM_MSG_TITLE_MUSIC_AUDIT_ERROR = "歌曲审核异常";
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_PASS = 1;
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_FAIL = 2;
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_ERROR = 3;
    public static final Byte SYSTEM_MSG_TYPE_TAG_APPROVED = 4;
    public static final String SYSTEM_MSG_TITLE_TAG_APPROVED = "标签申请已通过";

    private ForumBusinessConstants() {
    }
}
