package org.example.forumdemo.common.constant;

import java.util.Set;

// 论坛业务阈值、状态码、积分与 AI 模型等业务常量（非 Redis Key）
public final class ForumBusinessConstants {

    public static final Byte DELETE_STATE_TRUE = 1;
    public static final Byte STATE_BANNED = 1;

    public static final Byte MESSAGE_STATE_UNREAD = 0;
    public static final Byte MESSAGE_STATE_READ = 1;
    public static final Byte MESSAGE_STATE_RECALLED = 2;
    public static final long MESSAGE_RECALL_WINDOW_SECONDS = 120L;

    public static final Byte MESSAGE_TYPE_TEXT = 0;
    public static final Byte MESSAGE_TYPE_IMAGE = 1;
    public static final Byte MESSAGE_TYPE_GIF = 2;

    public static final Byte EMOJI_MEDIA_TYPE_IMAGE = 0;
    public static final Byte EMOJI_MEDIA_TYPE_GIF = 1;
    public static final int EMOJI_MAX_PER_USER = 200;

    public static final Byte REPLY_MEDIA_TYPE_IMAGE = 1;
    public static final Byte REPLY_MEDIA_TYPE_SHOP_EMOJI = 2;
    public static final int REPLY_MEDIA_MAX_IMAGES = 6;
    public static final int REPLY_MEDIA_MAX_EMOJIS = 5;

    public static final double HOT_SCORE_WEIGHT_LIKE = 1.0;
    public static final double HOT_SCORE_WEIGHT_VISIT = 1.0;
    public static final double HOT_SCORE_WEIGHT_FAVORITE = 0.7;
    public static final double HOT_SCORE_WEIGHT_REPLY = 0.5;

    /** 热帖榜只纳入最近 N 天发布的帖子 */
    public static final int HOT_RANK_WINDOW_DAYS = 7;
    /** 24 小时内新帖扶持系数 */
    public static final double HOT_RANK_NEW_POST_BOOST = 1.5;
    public static final int HOT_RANK_NEW_POST_HOURS = 24;
    /** 搜索关键词最大长度 */
    public static final int SEARCH_KEYWORD_MAX_LEN = 100;

    public static final int SMS_MAX_COUNT = 10;
    public static final int MAIL_MAX_COUNT = 10;

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
    public static final Set<String> IMAGE_SUPPORTED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif");
    public static final String IMAGE_TYPE_GIF = "image/gif";

    public static final int DANMAKU_MAX_PER_MINUTE = 20;
    public static final int DANMAKU_QUERY_MAX_WINDOW_MS = 120_000;
    public static final int DANMAKU_MAX_CONTENT_LEN = 30;

    public static final String SUMMARY_ARTICLE_NOT_FOUND = "帖子不存在或已被删除。";
    public static final String SUMMARY_ARTICLE_TOO_SHORT = "当前帖子内容较少（共 %d 字），建议包含更多内容后再尝试 AI 智能总结。";
    public static final String SUMMARY_AI_SERVICE_UNAVAILABLE = "AI 摘要生成暂时不可用，请稍后再试。";

    public static final Byte SHOP_STATUS_PENDING = 0;
    public static final Byte SHOP_STATUS_ONLINE = 1;
    public static final Byte SHOP_STATUS_OFFLINE = 2;

    public static final Byte POINTS_SOURCE_CHECKIN_BASIC = 0;
    public static final Byte POINTS_SOURCE_CHECKIN_BONUS = 1;
    public static final Byte POINTS_SOURCE_SHOP_PURCHASE = 2;
    public static final Byte POINTS_SOURCE_REFUND = 3;
    public static final Byte POINTS_SOURCE_LOTTERY_COST = 4;
    public static final Byte POINTS_SOURCE_LOTTERY_WIN = 5;
    public static final Byte POINTS_SOURCE_REGISTER_BONUS = 6;
    public static final Byte POINTS_SOURCE_VIP_SUBSCRIBE = 7;
    public static final Byte POINTS_SOURCE_LOTTERY_PAGE_SURPRISE = 8;
    public static final Byte POINTS_SOURCE_AI_COMPANION = 9;
    public static final Byte POINTS_SOURCE_AI_IMAGE = 10;
    public static final Byte POINTS_SOURCE_GAME_WIN = 11;
    public static final Byte POINTS_SOURCE_GAME_LOSE = 12;
    public static final Byte POINTS_SOURCE_TETRIS = 13;
    public static final Byte POINTS_SOURCE_ADMIN_ADJUST = 99;

    public static final int AI_ESTIMATE_CHAT_INPUT_TOKENS = 2000;
    public static final int AI_ESTIMATE_CHAT_OUTPUT_TOKENS = 1000;

    public static final String AI_MODEL_IMAGE_NORMAL = "z-image-turbo";
    public static final String AI_MODEL_IMAGE_PREMIUM = "gpt-image-2";
    public static final String AI_MODEL_QWEN_DEEP = "qwen3.7-max";
    public static final String AI_MODEL_GEMINI_DEEP = "gemini-3.1-pro";
    public static final String AI_MODEL_CLAUDE_HAIKU = "claude-haiku-4-5";
    public static final String AI_MODEL_CLAUDE_SONNET = "claude-sonnet-4-6";

    public static final int POINTS_LOTTERY_PAGE_SURPRISE_AMOUNT = 200;
    public static final int POINTS_REGISTER_BONUS_AMOUNT = 1000;
    public static final int VIP_PRICE_PRO_MONTH = 900;
    public static final int VIP_PRICE_MAX_MONTH = 2000;
    public static final Byte VIP_TIER_FREE = 0;
    public static final Byte VIP_TIER_PRO = 1;
    public static final Byte VIP_TIER_MAX = 2;

    public static final Byte LOTTERY_PRIZE_THANKS = 0;
    public static final Byte LOTTERY_PRIZE_GRAND = 1;
    public static final Byte LOTTERY_PRIZE_SMALL = 2;
    public static final Byte LOTTERY_PRIZE_CONSOLATION = 3;
    public static final Byte LOTTERY_PRIZE_POINTS = 4;
    public static final Byte LOTTERY_PRIZE_VIP_DAYS = 5;
    public static final int LOTTERY_PRIZE_SINGLE_POINTS_MAX = 100;
    public static final int LOTTERY_HARD_PITY_AFTER_MISSES = 50;

    public static final int EMOJI_SHOP_ITEM_MAX = 60;
    public static final int EMOJI_SHOP_PRICE_MIN = 0;
    public static final int EMOJI_SHOP_PRICE_MAX = 100_000;

    public static final int ARTICLE_AUDIT_MAX_RETRY = 3;
    public static final long ARTICLE_AUDIT_TIMEOUT_SECONDS = 600L;

    public static final String SYSTEM_MSG_TITLE_AUDIT_PASS = "帖子审核通过";
    public static final String SYSTEM_MSG_TITLE_AUDIT_FAIL = "帖子审核未通过";
    public static final String SYSTEM_MSG_TITLE_AUDIT_ERROR = "帖子审核异常";
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_PASS = 1;
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_FAIL = 2;
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_ERROR = 3;
    public static final Byte SYSTEM_MSG_TYPE_TAG_APPROVED = 4;
    public static final String SYSTEM_MSG_TITLE_TAG_APPROVED = "标签申请已通过";

    private ForumBusinessConstants() {
    }
}
