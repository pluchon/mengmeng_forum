package org.pluchon.forum.common.constant;

// OSS 业务路径前缀
public final class OssPaths {

    public static final String AVATAR = "forum_avatar_picture/";
    public static final String COVER = "forum_cover_picture/";
    public static final String BACKGROUND = "forum_profile_background_picture/";
    public static final String FAVORITE_FOLDER = "forum_favorite_folder_picture/";
    public static final String ARTICLE_IMAGE = "forum_article_picture/";
    public static final String VIDEO_ROOT = "forum_vedio/";
    public static final String ARTICLE_VIDEO = VIDEO_ROOT + "article_vedio/";
    public static final String ARTICLE_HLS = VIDEO_ROOT + "article_hls/";
    public static final String MUSIC = "music/";
    public static final String MUSIC_AVATAR = MUSIC + "music_avatar/";
    public static final String MUSIC_INFO = MUSIC + "music_info/";
    public static final String MUSIC_LRC = MUSIC + "music_lrc/";
    public static final String NOTICE_PICTURE = "forum_notice_picture/";
    public static final String LOTTERY_ACTIVITY = "forum_activity_picture/";
    public static final String LOTTERY_PRIZE = "forum_prize_picture/";
    public static final String CHAT_PICTURE_ROOT = "forum_chat_picture/";
    public static final String CHAT_MESSAGE = CHAT_PICTURE_ROOT + "message/";
    public static final String CHAT_EMOJI = CHAT_PICTURE_ROOT + "emoji/";
    public static final String EMOJI_SHOP = "forum_emoji_shop/";
    public static final String COMPANION_AI = "forum_companion_ai_picture/";
    public static final String AI_GENERATION_ROOT = "forum_ai_generation/";
    public static final String AI_GENERATION_ARTICLE = AI_GENERATION_ROOT + "article/";
    public static final String AI_GENERATION_SESSION = AI_GENERATION_ROOT + "session/";
    public static final String LEGACY_ROOT = "forum_db_item/";
    public static final String PENDING_SEGMENT = "_pending/";

    public static String pendingFolder(String businessPath) {
        if (businessPath == null || businessPath.isBlank()) {
            return PENDING_SEGMENT;
        }
        String folder = businessPath.endsWith("/") ? businessPath : businessPath + "/";
        return folder + PENDING_SEGMENT;
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
                NOTICE_PICTURE,
                LOTTERY_ACTIVITY,
                LOTTERY_PRIZE,
                CHAT_PICTURE_ROOT,
                CHAT_MESSAGE,
                CHAT_EMOJI,
                EMOJI_SHOP,
                COMPANION_AI,
                AI_GENERATION_ROOT,
                AI_GENERATION_ARTICLE,
                AI_GENERATION_SESSION,
        };
    }

    private OssPaths() {
    }
}
