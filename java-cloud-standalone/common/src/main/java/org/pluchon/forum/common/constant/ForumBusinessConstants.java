package org.pluchon.forum.common.constant;

import java.util.Set;

public final class ForumBusinessConstants {

    public static final Byte DELETE_STATE_TRUE = 1;
    public static final Byte DELETE_STATE_FALSE = 0;
    public static final Byte STATE_BANNED = 1;
    public static final Byte CREATOR_STATE_CERTIFIED = 1;

    public static final Byte MESSAGE_STATE_UNREAD = 0;
    public static final Byte MESSAGE_STATE_READ = 1;
    public static final Byte MESSAGE_STATE_RECALLED = 2;
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
    public static final int REPLY_MEDIA_MAX_TOTAL = 8;

    public static final double HOT_SCORE_WEIGHT_LIKE = 1.0;
    // 浏览是成本最低的行为：游客也算，IP 去重过期就能再计一次，全程不用登录。
    // 与点赞等权会让热帖榜实质变成浏览量榜，也把最容易刷的维度顶到了最前面
    public static final double HOT_SCORE_WEIGHT_VISIT = 0.1;
    public static final double HOT_SCORE_WEIGHT_FAVORITE = 0.7;
    public static final double HOT_SCORE_WEIGHT_REPLY = 0.5;

    // 创作中心 AI 搜索的向量兜底走的是全站索引，回来再裁到本人帖。
    // 帖子太少的创作者，全站语义命中里几乎不会有自己的，这次调用大概率裁成空
    public static final int CREATOR_VECTOR_FALLBACK_MIN_ARTICLES = 30;

    // AI 搜索一次最多打三次 Python（候选打分 / 向量兜底 / 作者相似兜底），
    // 且搜不到结果的查询三次全跑，是全站唯一没有配额的 AI 入口
    // 收藏夹名是公开可见的，等于一个文案位；不设上限的话可以无限建
    public static final int FAVORITE_FOLDER_MAX_COUNT = 20;

    // 推荐流一次算多少条并缓存。翻页只切这份缓存，不重跑召回
    public static final int RECOMMEND_FEED_CACHE_SIZE = 120;
    // 兴趣板块改动会立刻触发一次 AI 画像生成，在两组板块间来回切就能连续触发。
    // 冷却内只更新到期时间，交给每小时的定时任务去补
    public static final int RECOMMEND_PROFILE_REFRESH_COOLDOWN_SECONDS = 3600;
    // 候选板块种类太少时不做"相邻不同板块"打散：只选了一个兴趣板块的用户
    // 会被这条规则把自己最想看的内容全推到列表末尾
    public static final int RECOMMEND_DIVERSITY_MIN_BOARDS = 3;

    // 昵称/简介每提交一次就是一次 AI 文本审核，和 AI 搜索一样属于没配额的 AI 入口
    public static final int PROFILE_CHANGE_MAX_PER_DAY = 10;

    public static final int AI_SEARCH_MAX_PER_MINUTE = 10;
    public static final int AI_SEARCH_MAX_PER_DAY = 100;

    public static final int HOT_RANK_WINDOW_DAYS = 7;

    public static final double HOT_RANK_NEW_POST_BOOST = 1.5;
    public static final int HOT_RANK_NEW_POST_HOURS = 24;

    public static final int HOT_RANK_LIST_MAX = 28;
    public static final int HOT_RANK_PAGE_SIZE_MAX = 14;

    public static final double MUSIC_HOT_SCORE_WEIGHT_PLAY = 1.0;
    public static final double MUSIC_HOT_SCORE_WEIGHT_FAVORITE = 0.7;
    public static final double MUSIC_HOT_SCORE_DECAY_DAYS = 7.0;
    public static final double MUSIC_HOT_NEW_TRACK_BOOST = 1.3;
    public static final int MUSIC_HOT_NEW_TRACK_HOURS = 24;
    public static final int MUSIC_HOT_AUTHOR_MAX_PER_LIST = 2;
    public static final int SEARCH_KEYWORD_MAX_LEN = 100;

    public static final int SMS_MAX_COUNT = 10;
    public static final int MAIL_MAX_COUNT = 10;
    public static final int LOGIN_FAIL_MAX = 5;

    // 同一 IP 30 分钟内可发送的验证码总条数，短信 + 邮件合计
    public static final int CODE_SEND_IP_MAX_COUNT = 20;
    // 同一 IP 10 分钟内可生成的行为验证码次数
    public static final int CAPTCHA_IP_MAX_COUNT = 30;

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

    // 与 article 表 title varchar(100) / content text 对齐，避免超长直接撞数据库约束报 500
    // 下限只在"提交审核"时校验，草稿写一半也能存
    public static final int ARTICLE_TITLE_MIN_LEN = 3;
    public static final int ARTICLE_CONTENT_MIN_LEN = 6;
    public static final int ARTICLE_TITLE_MAX_LEN = 100;
    public static final int ARTICLE_CONTENT_MAX_LEN = 20000;

    // 送进大模型的正文上限：日配额只算次数不算 token，超长正文单次就能烧掉几十次的钱
    public static final int AI_INPUT_CONTENT_MAX_LEN = 20000;

    // 同一用户 30 分钟内可上传的图片总张数。上传接口不绑帖子，
    // 单次 9 张 / 落库 15 张都拦不住反复调用，而每张都要占 OSS 并过一次 AI 审图
    public static final int IMAGE_UPLOAD_USER_MAX_COUNT = 120;

    // octet-stream 会绕过 MIME 白名单，扩展名作为第二道
    public static final Set<String> VIDEO_SUPPORTED_EXTENSIONS = Set.of(
            ".mp4", ".mov", ".m4v", ".webm");
    // 转码超过这个时长仍停在 PROCESSING，视为任务丢失（多为服务重启），重新入队
    public static final int VIDEO_TRANSCODE_STALE_MINUTES = 40;
    // 单次兜底扫描最多重新入队的条数，避免一次把队列撑爆
    public static final int VIDEO_TRANSCODE_SWEEP_BATCH = 10;

    // 每次标签申请要烧三次 AI 调用（内容审核 + 向量粗排 + 相似度精判），
    // 且通过即建标签，不限次会同时敞开成本和标签池污染两个口子
    public static final int TAG_FEEDBACK_USER_MAX_COUNT = 10;
    // 标签允许的字符：中英数 + 技术标签常见的 + # . -（C++ / .NET / Vue3）
    public static final String TAG_NAME_PATTERN = "^[\\u4e00-\\u9fa5a-zA-Z0-9+#.-]{2,12}$";

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

    public static final long MUSIC_AUDIO_MAX_SIZE = 50L * 1024 * 1024;
    public static final long MUSIC_LRC_MAX_SIZE = (long) 1024 * 1024;
    public static final int MUSIC_TITLE_MAX_LEN = 100;
    public static final int MUSIC_LYRIC_TEXT_MAX_LEN = 100_000;
    // 一首歌最多挂 6 个氛围标签：播放器卡片一行放得下 3 个，再多就换行撑变形
    public static final int MUSIC_MOOD_TAG_MAX_COUNT = 6;
    public static final int MUSIC_MOOD_TAG_NAME_MAX_LEN = 8;
    // 曲库筛选栏的默认态，语义是「不过滤」，不是真实氛围，因此不进标签池
    public static final String MUSIC_MOOD_DEFAULT = "热门";
    // 每份草稿都会往 OSS 写一个最大 50MB 的音频，不设上限等于开着一个刷存储的口子
    public static final int MUSIC_DRAFT_MAX_COUNT = 20;
    // 推荐筛选最多勾几个氛围标签。再多的话 OR 召回等于没筛，
    // 命中数排序的区分度也会被稀释
    public static final int MUSIC_MOOD_FILTER_MAX = 5;
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

    public static final Byte SHOP_STATUS_PENDING = 0;
    public static final Byte SHOP_STATUS_ONLINE = 1;
    public static final Byte SHOP_STATUS_OFFLINE = 2;
    public static final Byte SHOP_STATUS_DRAFT = 3;

    public static final Byte POINTS_SOURCE_CHECKIN_BASIC = 0;
    public static final Byte POINTS_SOURCE_CHECKIN_BONUS = 1;
    public static final Byte POINTS_SOURCE_SHOP_PURCHASE = 2;
    public static final Byte POINTS_SOURCE_LOTTERY_COST = 4;
    public static final Byte POINTS_SOURCE_LOTTERY_WIN = 5;
    public static final Byte POINTS_SOURCE_REGISTER_BONUS = 6;
    public static final Byte POINTS_SOURCE_CHECKIN_SURPRISE = 14;
    public static final Byte POINTS_SOURCE_MILESTONE_REWARD = 15;

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
    // 群聊上限原本按群主 VIP 分三档。副作用很难看：群主 VIP 一到期，上限从 350
    // 掉回 100，一个 250 人的群立刻变成 OVER_LIMIT_LOCKED——群里的人什么都没做，
    // 却因为群主没续费而进不来新人。等于用付费状态惩罚无关的第三方。
    // 统一取原最高档，取消一个权益不应该让任何人变差，存量群也不会被锁。
    public static final int GROUP_CHAT_CREATE_LIMIT = 30;
    public static final int GROUP_CHAT_MEMBER_LIMIT = 350;
    public static final int GROUP_CHAT_NAME_MAX_LEN = 10;
    public static final int GROUP_CHAT_INTRO_MAX_LEN = 120;
    public static final int GROUP_CHAT_MESSAGE_MAX_LEN = 500;

    public static final Byte LOTTERY_PRIZE_THANKS = 0;
    public static final Byte LOTTERY_PRIZE_GRAND = 1;
    public static final Byte LOTTERY_PRIZE_SMALL = 2;
    public static final Byte LOTTERY_PRIZE_CONSOLATION = 3;
    public static final Byte LOTTERY_PRIZE_POINTS = 4;
    public static final Byte LOTTERY_PRIZE_VIP_DAYS = 5;
    public static final int LOTTERY_RANDOM_POINTS_MARKER = -1;
    public static final int LOTTERY_RANDOM_POINTS_MIN = 10;
    public static final int LOTTERY_RANDOM_POINTS_MAX = 50;
    public static final int LOTTERY_HARD_PITY_AFTER_MISSES = 50;

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
    public static final Byte POINTS_SOURCE_LOTTERY_COLLECT = 16;

    public static final int EMOJI_SHOP_ITEM_MAX = 60;
    public static final int EMOJI_SHOP_PRICE_MIN = 0;
    public static final int EMOJI_SHOP_PRICE_MAX = 5_000;
    // 草稿没有上限时可以无限新建，每条还带一批图片关联
    public static final int EMOJI_SHOP_DRAFT_MAX_COUNT = 20;

    public static final int ARTICLE_AUDIT_MAX_RETRY = 3;
    // 单篇帖子每日提交审核上限。ARTICLE_AUDIT_MAX_RETRY 会被编辑重置，
    // 这个不会，用来挡住「改一下就又有 3 次」的无限磨审核
    public static final int ARTICLE_AUDIT_DAILY_MAX = 8;
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
