package org.pluchon.forum.common.constant;

// OSS 业务路径前缀 相对 key；根前缀见 oss.root prefix
public final class OssPaths {

    public static final String AVATAR = "forum_avatar_picture/";
    public static final String COVER = "forum_cover_picture/";
    public static final String BACKGROUND = "forum_profile_background_picture/";
    public static final String FAVORITE_FOLDER = "forum_favorite_folder_picture/";
    public static final String ARTICLE_IMAGE = "forum_article_picture/";
    public static final String ARTICLE_VIDEO = "forum_vedio/article_vedio/";
    public static final String ARTICLE_HLS = "forum_vedio/article_hls/";
    // 帖子配乐 OSS：均在 music/ 下，用户上传与曲库共用这三层
    // 用户上传三件套同 stem：{歌曲名}_{userId}_{yyyyMMddHHmmss}
    // 历史曲库可能仍是纯歌名 / 封面「歌名_时间」
    public static final String MUSIC = "music/";
    public static final String MUSIC_AVATAR = "music/music_avatar/";
    public static final String MUSIC_INFO = "music/music_info/";
    public static final String MUSIC_LRC = "music/music_lrc/";
    public static final String NOTICE_PICTURE = "forum_notice_picture/";
    public static final String LOTTERY_ACTIVITY = "forum_activity_picture/";
    public static final String LOTTERY_PRIZE = "forum_prize_picture/";
    public static final String CHAT_MESSAGE = "forum_chat_picture/message/";
    public static final String CHAT_EMOJI = "forum_chat_picture/emoji/";
    public static final String EMOJI_SHOP = "forum_emoji_shop/";
    public static final String COMPANION_AI = "forum_companion_ai_picture/";
    public static final String AI_GENERATION_ARTICLE = "forum_ai_generation/article/";
    public static final String AI_GENERATION_SESSION = "forum_ai_generation/session/";
    public static final String LEGACY_ROOT = "forum_db_item/";
    public static final String PENDING_SEGMENT = "_pending/";

    // 审图前临时目录：{businessPath}_pending/ ；审图通过后 copy 到正式目录，失败同请求 delete；OSS lifecycle 兜底孤儿
    public static String pendingFolder(String businessPath) {
        if (businessPath == null || businessPath.isBlank()) {
            return PENDING_SEGMENT;
        }
        String folder = businessPath.endsWith("/") ? businessPath : businessPath + "/";
        return folder + PENDING_SEGMENT;
    }

    // 业务上传目录 不含历史 LEGACY_ROOT ；用于启动/上传时按需创建 OSS「文件夹」占位对象
    public static String[] allBusinessPaths() {
        return new String[]{
                AVATAR,
                COVER,
                BACKGROUND,
                FAVORITE_FOLDER,
                ARTICLE_IMAGE,
                // 与控制台层次一致：先父目录再子目录 拼写保持 vedio
                "forum_vedio/",
                ARTICLE_VIDEO,
                ARTICLE_HLS,
                MUSIC,
                MUSIC_AVATAR,
                MUSIC_INFO,
                MUSIC_LRC,
                NOTICE_PICTURE,
                LOTTERY_ACTIVITY,
                LOTTERY_PRIZE,
                "forum_chat_picture/",
                CHAT_MESSAGE,
                CHAT_EMOJI,
                EMOJI_SHOP,
                COMPANION_AI,
                "forum_ai_generation/",
                AI_GENERATION_ARTICLE,
                AI_GENERATION_SESSION,
        };
    }

    private OssPaths() {
    }
}
