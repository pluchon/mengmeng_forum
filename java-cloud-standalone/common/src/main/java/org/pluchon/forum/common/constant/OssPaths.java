package org.pluchon.forum.common.constant;

// OSS 业务路径前缀
public final class OssPaths {

    public static final String AVATAR = "forum_avatar_picture/";
    public static final String COVER = "forum_cover_picture/";
    public static final String BACKGROUND = "forum_profile_background_picture/";
    public static final String FAVORITE_FOLDER = "forum_favorite_folder_picture/";
    public static final String ARTICLE_IMAGE = "forum_article_picture/";
    private static final String VIDEO_ROOT = "forum_vedio/";
    public static final String ARTICLE_VIDEO = VIDEO_ROOT + "article_vedio/";
    public static final String ARTICLE_HLS = VIDEO_ROOT + "article_hls/";
    public static final String MUSIC = "music/";
    public static final String MUSIC_AVATAR = MUSIC + "music_avatar/";
    public static final String MUSIC_INFO = MUSIC + "music_info/";
    public static final String MUSIC_LRC = MUSIC + "music_lrc/";
    private static final String CHAT_PICTURE_ROOT = "forum_chat_picture/";
    public static final String CHAT_MESSAGE = CHAT_PICTURE_ROOT + "message/";
    public static final String CHAT_EMOJI = CHAT_PICTURE_ROOT + "emoji/";
    public static final String EMOJI_SHOP = "forum_emoji_shop/";
    private static final String AI_GENERATION_ROOT = "forum_ai_generation/";
    public static final String AI_GENERATION_ARTICLE = AI_GENERATION_ROOT + "article/";
    public static final String AI_GENERATION_SESSION = AI_GENERATION_ROOT + "session/";
    public static final String LEGACY_ROOT = "forum_db_item/";
    public static final String PENDING_SEGMENT = "_pending/";
    // 判违规下架的对象搬到这里：播放立刻 404，再由生命周期规则按天数真删。
    // 和 _pending/ 同构，同样必须在顶层——OSS 的前缀是字面匹配，不认中间通配
    public static final String REMOVED_SEGMENT = "_removed/";

    // 待定区放在**顶层**：_pending/{业务目录}/。
    // 这样 OSS 生命周期规则只要一条 `_pending/` 前缀就能覆盖全部业务目录，
    // 以后新增目录自动生效，不用再去控制台补规则。
    // 注意：OSS 的前缀是字面匹配、不支持中间通配，写成 {业务目录}/_pending/ 就得配十几条。
    public static String pendingFolder(String businessPath) {
        if (businessPath == null || businessPath.isBlank()) {
            return PENDING_SEGMENT;
        }
        String folder = businessPath.endsWith("/") ? businessPath : businessPath + "/";
        return PENDING_SEGMENT + folder;
    }

    // 违规下架区，和 pendingFolder 同构
    public static String removedFolder(String businessPath) {
        if (businessPath == null || businessPath.isBlank()) {
            return REMOVED_SEGMENT;
        }
        String folder = businessPath.endsWith("/") ? businessPath : businessPath + "/";
        return REMOVED_SEGMENT + folder;
    }

    public static String[] allBusinessPaths() {
        return new String[]{
                AVATAR,
                COVER,
                BACKGROUND,
                FAVORITE_FOLDER,
                ARTICLE_IMAGE,
                VIDEO_ROOT,
                ARTICLE_VIDEO,
                ARTICLE_HLS,
                MUSIC,
                MUSIC_AVATAR,
                MUSIC_INFO,
                MUSIC_LRC,
                CHAT_PICTURE_ROOT,
                CHAT_MESSAGE,
                CHAT_EMOJI,
                EMOJI_SHOP,
                AI_GENERATION_ROOT,
                AI_GENERATION_ARTICLE,
                AI_GENERATION_SESSION,
        };
    }

    private OssPaths() {
    }
}
