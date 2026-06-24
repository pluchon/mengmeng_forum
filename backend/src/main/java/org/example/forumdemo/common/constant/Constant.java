package org.example.forumdemo.common.constant;

import java.util.Set;
import java.util.regex.Pattern;

// 定义全局变量
public class Constant {
    // 用户信息key值
    public static final String USER_SESSION = "user_session";

    // 校验用户用户名的合法性
    public static final Pattern VALID_USERNAME_PATTERN = Pattern
            .compile("^[\\u4e00-\\u9fa5a-zA-Z0-9][\\u4e00-\\u9fa5a-zA-Z0-9_-]{2,18}[\\u4e00-\\u9fa5a-zA-Z0-9]$");

    // 令牌名称（Header/Cookie 中存储 JWT 的 key）
    public static final String JWT_NAME = "Authorization";

    // JWT 载荷 Payload 中的 Key 字段
    public static final String JWT_USER_ID = "userId";
    public static final String JWT_USER_NAME = "username";
    /** JWT 版本号，与 Redis forum:jwt:tv:{userId} 对齐；改密/禁言后递增 */
    public static final String JWT_TOKEN_VERSION = "tv";

    // 跨域暴露请求头常量
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    // =================== RabbitMQ Exchange 名称 ===================
    // 主题交换机：负责将业务消息按 RoutingKey 路由到对应队列（帖子回复、私信等）
    public static final String TOPIC_EXCHANGE_1 = "t_exchange_1";
    // 死信交换机：专门接收从业务队列中过期或被拒绝的死信消息
    public static final String DEATH_EXCHANGE_1 = "d-exchange_1";

    // =================== RabbitMQ Queue 名称 ===================
    // 业务仲裁队列 1：接收帖子回复通知等实时消息（绑定到主题交换机）
    public static final String QUORUM_QUEUE_1 = "q-queue_1";
    // 业务仲裁队列 2：接收私信消息（绑定到主题交换机，支持扩展）
    public static final String QUORUM_QUEUE_2 = "q-queue_2";
    // 业务仲裁队列 3：Java -> Python 帖子异步审核任务下发队列
    public static final String QUORUM_QUEUE_AUDIT_TASK   = "q-audit-article";
    // 业务仲裁队列 4：Python -> Java 帖子审核结果回执队列
    public static final String QUORUM_QUEUE_AUDIT_RESULT = "q-audit-result";
    // 游戏对局结束事件队列：承接结算后的通知、统计和榜单刷新入口
    public static final String QUORUM_QUEUE_GAME_FINISHED = "q-game-finished";
    // 死信队列：接收过期或超出积压限制的消息（绑定到死信交换机）
    public static final String D_QUORUM_QUEUE_1 = "d-queue_1";

    // =================== RabbitMQ RoutingKey 常量 ===================
    // 主题交换机 -> 业务队列 1 的路由键（匹配帖子回复消息）
    public static final String ROUTING_KEY_QUEUE_1 = "forum.notify.reply";
    // 主题交换机 -> 业务队列 2 的路由键（匹配私信消息）
    public static final String ROUTING_KEY_QUEUE_2 = "forum.notify.message";
    // 主题交换机 -> 审核任务队列 (Java 投递, Python 消费)
    public static final String ROUTING_KEY_AUDIT_TASK   = "forum.audit.article";
    // 主题交换机 -> 审核结果队列 (Python 回执, Java 消费)
    public static final String ROUTING_KEY_AUDIT_RESULT = "forum.audit.result";
    // 主题交换机 -> 游戏结束事件队列
    public static final String ROUTING_KEY_GAME_FINISHED = "forum.game.finished";
    // 死信消息的路由键：消息过期后由死信交换机使用此 Key 路由到死信队列
    public static final String ROUTING_KEY_DEAD = "forum.dead.#";

    // =================== Redis Key 前缀 ===================
    // 用户详情 Hash 缓存前缀，格式：user_info:{userId}
    //WebSocket 跨实例推送 Redis Pub/Sub 频道
    public static final String WS_PUSH_CHANNEL = "forum:ws:push";
    // 游戏房间跨实例事件广播频道
    public static final String GAME_ROOM_EVENT_CHANNEL = "forum:game:room:event";

    public static final String REDIS_KEY_JWT_TOKEN_VERSION = "forum:jwt:tv:";
    public static final String REDIS_KEY_USER_INFO = "user_info:";
    // 用户名 -> userId String 映射前缀，格式：user_name:{username}
    public static final String REDIS_KEY_USER_NAME = "user_name:";
    // 用户点赞帖子 Set 缓存前缀，格式：user_likes:{userId}
    public static final String REDIS_KEY_USER_LIKES = "user_likes:";
    // 缓存穿透保护：查库不存在时存入的空标记值
    public static final String REDIS_EMPTY_MARK = "NOT_FOUND";

    // =================== Redis TTL（单位：秒）===================
    // 用户详情 Hash 缓存有效期：300s
    public static final long REDIS_TTL_USER_INFO = 300L;
    // 用户名 -> userId 映射有效期：与用户详情保持一致
    public static final long REDIS_TTL_USER_NAME = 300L;
    // 缓存穿透空标记有效期：30s（短TTL防止正常注册被屏蔽）
    public static final long REDIS_TTL_EMPTY_MARK = 30L;
    // 用户点赞 Set 缓存有效期：600s
    public static final long REDIS_TTL_USER_LIKES = 600L;

    // =================== 消息和状态常量 ===================
    // 删除状态：1 表示已删除
    public static final Byte DELETE_STATE_TRUE = 1;
    // 状态：表示被禁用/封禁
    public static final Byte STATE_BANNED = 1;
    // 私信状态：0 表示未读
    public static final Byte MESSAGE_STATE_UNREAD = 0;
    // 私信状态：1 表示已读
    public static final Byte MESSAGE_STATE_READ = 1;
    // 私信状态：2 表示已撤回（2分钟内可撤回）；删除语义交给 delete_state
    public static final Byte MESSAGE_STATE_RECALLED = 2;
    // 撤回时间窗口：120秒
    public static final long MESSAGE_RECALL_WINDOW_SECONDS = 120L;

    // =================== 私信消息类型 ===================
    // 文本消息(默认): content 必填, media_* 全部为空
    public static final Byte MESSAGE_TYPE_TEXT  = 0;
    // 图片消息(JPG/PNG): content 必为空, media_url 必填
    public static final Byte MESSAGE_TYPE_IMAGE = 1;
    // GIF 动图: 同 IMAGE 字段约束, 单独区分以便前端渲染播放控件
    public static final Byte MESSAGE_TYPE_GIF   = 2;

    // =================== 表情收藏 ===================
    // 表情媒体类型: 0=静态图(JPG/PNG), 1=GIF
    public static final Byte EMOJI_MEDIA_TYPE_IMAGE = 0;
    public static final Byte EMOJI_MEDIA_TYPE_GIF   = 1;
    // 单用户表情收藏数量上限(防滥刷; 业务层校验, 非数据库约束)
    public static final int EMOJI_MAX_PER_USER = 200;

    // 帖子被点赞的用户集合，格式：article_likers:{articleId}
    // SRANDMEMBER 随机取N个，展示在文章卡片上，点击跳转用户主页
    public static final String REDIS_KEY_ARTICLE_LIKERS = "article_likers:";
    // 热帖排行榜 ZSet，格式：hot:articles
    // score = like_count*WEIGHT_LIKE + visit_count*WEIGHT_VISIT + favorite_count*WEIGHT_FAVORITE
    //       + (reply_count + sub_reply_count)*WEIGHT_REPLY
    // 由各业务点增量维护; HotArticleRankingTask 每天凌晨 3 点全量重算兜底
    public static final String REDIS_KEY_HOT_ARTICLES = "hot:articles";
    // 文章点赞用户集合TTL：1800s（30分钟）
    public static final long REDIS_TTL_ARTICLE_LIKERS = 1800L;

    // 热帖榜加权系数: 点赞 ≈ 浏览 > 收藏 > 评论
    public static final double HOT_SCORE_WEIGHT_LIKE     = 1.0;
    public static final double HOT_SCORE_WEIGHT_VISIT    = 1.0;
    public static final double HOT_SCORE_WEIGHT_FAVORITE = 0.7;
    public static final double HOT_SCORE_WEIGHT_REPLY    = 0.5;

    // =================== SMS 限流 ===================
    // 短信验证码前缀 (绑定/登录使用)
    public static final String REDIS_KEY_SMS_VERIFY = "v-bind-";
    // 短信验证码前缀 (重置密码使用)
    public static final String REDIS_KEY_SMS_VERIFY_RESET = "v-reset-";
    // 短信发送冷却前缀
    public static final String REDIS_KEY_SMS_COOLDOWN = "sms_cd:";
    // 短信发送计数器前缀，格式：sms_count:{phoneNumber}
    public static final String REDIS_KEY_SMS_COUNT = "sms_count:";
    // 短信发送计数器TTL：1800s（30分钟窗口）
    public static final long REDIS_TTL_SMS_COUNT = 1800L;
    // 30分钟内最大发送次数
    public static final int SMS_MAX_COUNT = 10;

    // =================== Mail 限流 ===================
    // 邮箱验证码前缀 (绑定/登录使用)
    public static final String REDIS_KEY_MAIL_VERIFY = "m-bind-";
    // 邮箱验证码前缀 (重置密码使用)
    public static final String REDIS_KEY_MAIL_VERIFY_RESET = "m-reset-";
    // 邮箱发送冷却前缀
    public static final String REDIS_KEY_MAIL_COOLDOWN = "mail_cd:";
    // 邮件发送计数器前缀，格式：mail_count:{email}
    public static final String REDIS_KEY_MAIL_COUNT = "mail_count:";
    // 邮件发送计数器TTL：1800s（30分钟窗口）
    public static final long REDIS_TTL_MAIL_COUNT = 1800L;
    // 30分钟内最大发送次数
    public static final int MAIL_MAX_COUNT = 10;

    // =================== 板块缓存 ===================
    // 板块列表缓存前缀，格式：board_list:{orderBy}
    public static final String REDIS_KEY_BOARD_LIST = "board_list:";
    // 板块列表TTL：3600s（1小时），因为板块信息变动极小
    public static final long REDIS_TTL_BOARD_LIST = 3600L;

    // =================== AI 服务路由地址 ===================
    // 同步审核 / RAG / 摘要基址见 forum.ai.hub-base-url（AiHubUrls），勿再写死 localhost

    /** 看板娘对话: Java BFF -> Python ai-server, URL 以 application.yml forum.mascot.ai-url 为准 */

    /** 普通用户看板娘每日对话计数: mascot:daily:chat:{yyyyMMdd}:{userId} */
    public static final String REDIS_KEY_MASCOT_DAILY_CHAT = "mascot:daily:chat:";

    // RAG 检索召回侧粗筛: 取最近 N=100 篇已发布帖子做候选; 太大会让 rerank 调用超时
    public static final int SEARCH_RAG_CANDIDATE_LIMIT = 80;
    /** 倒排召回最大帖子数 */
    public static final int SEARCH_INVERTED_MAX_RESULTS = 120;
    // 单次返回的最大 RAG 结果数 (上限保护, 防超大 pageSize 把 rerank 打满)
    public static final int SEARCH_RAG_MAX_RESULTS = 50;
    // 搜索响应的 source 标识
    public static final String SEARCH_SOURCE_DB    = "db";
    public static final String SEARCH_SOURCE_INV   = "inv";
    public static final String SEARCH_SOURCE_RAG   = "rag";
    public static final String SEARCH_SOURCE_EMPTY = "empty";

    // =================== OSS 业务路径前缀（相对 key；根前缀见 oss.root-prefix / OSS_ROOT_PREFIX） ===================
    public static final String OSS_PATH_AVATAR     = "forum_avatar_picture/";
    public static final String OSS_PATH_COVER      = "forum_cover_picture/";
    public static final String OSS_PATH_BACKGROUND = "forum_profile_background_picture/";
    public static final String OSS_PATH_ARTICLE_IMAGE = "forum_article_picture/";
    /** 帖子视频：对象名 {userId}_{yyyyMMddHHmmss}.mp4（大于100MB时先压缩） */
    public static final String OSS_PATH_ARTICLE_VIDEO = "forum_vedio/article_vedio/";
    /** 公告中心卡片配图：对象名 {发布者用户ID}_{公告ID}_{yyyyMMddHHmmss}.{ext} */
    public static final String OSS_PATH_NOTICE_PICTURE = "forum_notice_picture/";
    public static final String OSS_PATH_LOTTERY_ACTIVITY = "forum_activity_picture/";
    public static final String OSS_PATH_LOTTERY_PRIZE = "forum_prize_picture/";
    public static final String OSS_PATH_CHAT_MESSAGE = "forum_chat_picture/message/";
    public static final String OSS_PATH_CHAT_EMOJI   = "forum_chat_picture/emoji/";
    public static final String OSS_PATH_EMOJI_SHOP   = "forum_emoji_shop/";
    /** 看板娘 / AI 生图落库（避免超长外链或 data URL 写入 DB） */
    public static final String OSS_PATH_COMPANION_AI = "forum_companion_ai_picture/";

    /** AI 生图：文章封面 */
    public static final String OSS_PATH_AI_GENERATION_ARTICLE = "forum_ai_generation/article/";
    /** AI 生图：看板娘 / 陪伴对话 */
    public static final String OSS_PATH_AI_GENERATION_SESSION = "forum_ai_generation/session/";
    /** 旧版统一根目录（兼容历史 URL 校验） */
    public static final String OSS_LEGACY_ROOT = "forum_db_item/";

    // =================== 帖子相册 ===================
    // 帖子相册图片上限: 单个帖子最多 15 张, 由 ArticleService.replaceArticleImages 强约束
    public static final int ARTICLE_GALLERY_MAX = 15;
    // 当帖子带有相册图时, 正文最少字符数; 防"图多字少"水帖
    public static final int ARTICLE_GALLERY_MIN_CONTENT_LEN = 10;

    // =================== 图片上传 / 压缩 ===================
    // 业务期望: 落到 OSS 的图片不超过 5MB (静态图压缩目标), 也是直接放行 / 触发压缩的分界线
    public static final long OSS_MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    // 服务器接收的硬上限: 30MB, 超过此大小直接拒收 (与 application.yml 的 max-file-size 保持一致)
    public static final long IMAGE_HARD_MAX_SIZE = 30L * 1024 * 1024;
    // GIF 动图特殊上限: 15MB, 不参与压缩 (压缩会丢帧变静态图)
    public static final long IMAGE_GIF_MAX_SIZE = 15L * 1024 * 1024;
    // 静态图压缩目标: 略低于 5MB (留 buffer 防边缘溢出)
    public static final long IMAGE_COMPRESS_TARGET_SIZE = (long) (4.8 * 1024 * 1024);
    // 压缩后允许写入 OSS 的最大尺寸 (略高于 target, 仅做保险检查; 压缩工具兜底失败时拒收)
    public static final long IMAGE_COMPRESS_MAX_OUTPUT_SIZE = OSS_MAX_IMAGE_SIZE;
    // 压缩时允许的最大边长 (px), 超过则等比缩放
    public static final int IMAGE_COMPRESS_MAX_DIMENSION = 2560;
    // 支持上传的图片 MIME 类型白名单
    public static final Set<String> IMAGE_SUPPORTED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/gif");
    // GIF 类型 (用于跳过压缩 + 单独限尺寸)
    public static final String IMAGE_TYPE_GIF = "image/gif";

    // =================== 私信缓存 ===================
    // 私信会话列表缓存前缀，格式：message_sessions:{userId}
    public static final String REDIS_KEY_MESSAGE_SESSIONS = "message_sessions:";
    // 私信未读计数缓存前缀，格式：message_unread_count:{userId}
    public static final String REDIS_KEY_MESSAGE_UNREAD_COUNT = "message_unread_count:";
    // 私信会话列表TTL：300s（5分钟），变动较频繁，配合主动失效
    public static final long REDIS_TTL_MESSAGE_SESSIONS = 300L;
    // 用户表情收藏列表缓存前缀，格式：user_emoji:{userId}
    public static final String REDIS_KEY_USER_EMOJI_LIST = "user_emoji:";
    // 表情收藏列表TTL：600s（10分钟），收藏/取消时主动失效
    public static final long REDIS_TTL_USER_EMOJI_LIST = 600L;

    // =================== 帖子摘要缓存 ===================
    // 帖子摘要缓存前缀，格式：article_summary:{articleId}
    public static final String REDIS_KEY_ARTICLE_SUMMARY = "article_summary:";
    // 帖子摘要TTL：3600s（1小时）
    public static final long REDIS_TTL_ARTICLE_SUMMARY = 3600L;

    /** 用户触发的 AI 智能导读缓存（与审核摘要 article_summary 分离，避免覆盖引发并发问题） */
    public static final String REDIS_KEY_ARTICLE_GUIDE = "article_guide:";
    public static final long REDIS_TTL_ARTICLE_GUIDE = 604800L;

    // =================== AI 摘要提示语常量 ===================
    public static final String SUMMARY_ARTICLE_NOT_FOUND = "帖子不存在或已被删除。";
    public static final String SUMMARY_ARTICLE_TOO_SHORT = "当前帖子内容较少（共 %d 字），建议包含更多内容后再尝试 AI 智能总结。";
    public static final String SUMMARY_AI_SERVICE_UNAVAILABLE = "AI 摘要生成暂时不可用，请稍后再试。";

    // =================== 签到模块缓存 ===================
    // 签到状态响应缓存前缀，格式：checkin:status:{userId}; 命中即省一次 SELECT user_checkin_info
    public static final String REDIS_KEY_CHECKIN_STATUS = "checkin:status:";
    // 签到状态缓存 TTL：600s (10 分钟); doCheckin 成功后主动失效, 不依赖自然过期
    public static final long REDIS_TTL_CHECKIN_STATUS = 600L;
    // 月度签到规则缓存前缀，格式：checkin:rule:m{1-12}; 命中即省一次实体->VO 的转换+排序
    public static final String REDIS_KEY_CHECKIN_RULE = "checkin:rule:m";
    // 月度签到规则缓存 TTL：21600s (6 小时); 规则变更频率极低, 长 TTL 即可
    public static final long REDIS_TTL_CHECKIN_RULE = 21600L;

    // =================== 表情包商城 / 积分钱包 ===================
    // emoji_shop.status
    public static final Byte SHOP_STATUS_PENDING = 0; // 预留: 待审核(AI 同步审核暂未使用)
    public static final Byte SHOP_STATUS_ONLINE  = 1; // 上架
    public static final Byte SHOP_STATUS_OFFLINE = 2; // 下架

    // points_log.source_type
    public static final Byte POINTS_SOURCE_CHECKIN_BASIC  = 0;  // 签到基础分
    public static final Byte POINTS_SOURCE_CHECKIN_BONUS  = 1;  // 签到连续奖励
    public static final Byte POINTS_SOURCE_SHOP_PURCHASE  = 2;  // 商城购买
    public static final Byte POINTS_SOURCE_REFUND         = 3;  // 退款回补(预留, 当前未开放)
    public static final Byte POINTS_SOURCE_LOTTERY_COST   = 4;  // 积分抽奖消耗
    public static final Byte POINTS_SOURCE_LOTTERY_WIN    = 5;  // 积分抽奖中奖入账
    public static final Byte POINTS_SOURCE_REGISTER_BONUS = 6;  // 新用户注册赠送
    public static final Byte POINTS_SOURCE_VIP_SUBSCRIBE   = 7;  // VIP 订阅扣款
    /** 抽奖页「点我看看」彩蛋一次性积分（user.lottery_surprise_claimed 控制幂等） */
    public static final Byte POINTS_SOURCE_LOTTERY_PAGE_SURPRISE = 8;
    /** AI 陪伴助手对话消耗 */
    public static final Byte POINTS_SOURCE_AI_COMPANION = 9;
    /** AI 生图消耗 */
    public static final Byte POINTS_SOURCE_AI_IMAGE = 10;
    /** 游戏胜利奖励 */
    public static final Byte POINTS_SOURCE_GAME_WIN = 11;
    /** 游戏失败扣除 */
    public static final Byte POINTS_SOURCE_GAME_LOSE = 12;
    public static final Byte POINTS_SOURCE_ADMIN_ADJUST   = 99; // 管理员调整(预留)

    /** 陪伴对话默认估算 token（无 usage 回传时） */
    public static final int AI_ESTIMATE_CHAT_INPUT_TOKENS = 2000;
    public static final int AI_ESTIMATE_CHAT_OUTPUT_TOKENS = 1000;

    /** 生图：普通档 Dashscope；进阶档 GPT Image（HuanAPI） */
    public static final String AI_MODEL_IMAGE_NORMAL = "z-image-turbo";
    public static final String AI_MODEL_IMAGE_PREMIUM = "gpt-image-2";
    /** HuanAPI 文本模型 */
    public static final String AI_MODEL_QWEN_DEEP = "qwen3.7-max";
    public static final String AI_MODEL_GEMINI_DEEP = "gemini-3.1-pro";
    public static final String AI_MODEL_CLAUDE_HAIKU = "claude-haiku-4-5";
    public static final String AI_MODEL_CLAUDE_SONNET = "claude-sonnet-4-6";

    /** 抽奖页彩蛋一次性发放的积分数量 */
    public static final int POINTS_LOTTERY_PAGE_SURPRISE_AMOUNT = 200;

    /** 新用户注册赠送积分数量 */
    public static final int POINTS_REGISTER_BONUS_AMOUNT = 1000;
    /** VIP PRO / MAX 月度订阅积分价 */
    public static final int VIP_PRICE_PRO_MONTH = 900;
    public static final int VIP_PRICE_MAX_MONTH = 2000;
    /** VIP 档位: 与 user.vip_tier 一致 */
    public static final Byte VIP_TIER_FREE = 0;
    public static final Byte VIP_TIER_PRO = 1;
    public static final Byte VIP_TIER_MAX = 2;

    // lottery_prize.prize_type: 0谢谢 1大奖 2小奖 3安慰奖 4积分 5VIP天
    public static final Byte LOTTERY_PRIZE_THANKS = 0;
    public static final Byte LOTTERY_PRIZE_GRAND = 1;
    public static final Byte LOTTERY_PRIZE_SMALL = 2;
    public static final Byte LOTTERY_PRIZE_CONSOLATION = 3;
    public static final Byte LOTTERY_PRIZE_POINTS = 4;
    public static final Byte LOTTERY_PRIZE_VIP_DAYS = 5;
    /** lottery_prize / 神秘子项 中单档积分奖上限 */
    public static final int LOTTERY_PRIZE_SINGLE_POINTS_MAX = 100;

    /** 抽奖硬保底：连续未命中「神秘大奖」父档(is_jackpot=1) 达到此次数后，下一次强制命中该档（持久化字段 user.lottery_pity_draws） */
    public static final int LOTTERY_HARD_PITY_AFTER_MISSES = 50;

    // 单个表情包内允许的最大图片数
    public static final int EMOJI_SHOP_ITEM_MAX = 60;
    // 单个表情包售价上下限(防误录入天价 / 0 分白嫖)
    public static final int EMOJI_SHOP_PRICE_MIN = 0;
    public static final int EMOJI_SHOP_PRICE_MAX = 100_000;

    // 商城商品详情缓存前缀，格式：shop:detail:{shopId}; 上下架 / 销量增长后主动失效
    // (当前只在 updateStatus 时主动失效, 读路径暂未引入此缓存, 留出后续接入空间)
    public static final String REDIS_KEY_SHOP_DETAIL = "shop:detail:";
    // 商品详情 TTL：1800s (30 分钟)
    public static final long REDIS_TTL_SHOP_DETAIL = 1800L;

    // =================== 帖子异步审核 ===================
    // 单帖累计提交审核上限; 超过则提示"已超出审核次数, 请联系管理员"
    public static final int ARTICLE_AUDIT_MAX_RETRY = 3;
    // 审核任务超时兜底阈值(秒); ArticleAuditTimeoutTask 扫描 PENDING 超过此时长的帖子, 自动转 AUDIT_ERROR
    public static final long ARTICLE_AUDIT_TIMEOUT_SECONDS = 600L;
    // 审核任务结果缓存(Redis); 用于 Java 侧消费 result 时幂等去重, 防止 Python 因网络重投触发多次状态扭转
    public static final String REDIS_KEY_AUDIT_RESULT_DEDUP = "ai_audit:result_dedup:";
    public static final long REDIS_TTL_AUDIT_RESULT_DEDUP = 86400L;
    // 审核任务正在跑的标记 / 完成标记: Python 侧 SETNX 写入, 用于幂等
    public static final String REDIS_KEY_AUDIT_TASK_STATE  = "ai_audit:task_state:";
    // 系统消息标题前缀(用户看到的标题里直接用)
    public static final String SYSTEM_MSG_TITLE_AUDIT_PASS  = "帖子审核通过";
    public static final String SYSTEM_MSG_TITLE_AUDIT_FAIL  = "帖子审核未通过";
    public static final String SYSTEM_MSG_TITLE_AUDIT_ERROR = "帖子审核异常";

    // system_message.type
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_PASS  = 1;
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_FAIL  = 2;
    public static final Byte SYSTEM_MSG_TYPE_AUDIT_ERROR = 3;
    /** 用户申请的新帖子标签已通过审核 */
    public static final Byte SYSTEM_MSG_TYPE_TAG_APPROVED = 4;
    public static final String SYSTEM_MSG_TITLE_TAG_APPROVED = "标签申请已通过";
}
