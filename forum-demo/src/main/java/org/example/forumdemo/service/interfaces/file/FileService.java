package org.example.forumdemo.service.interfaces.file;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    // 上传帖子封面，返回OSS URL
    String uploadCoverImage(MultipartFile file, Long userId);
    // 上传用户头像，返回OSS URL
    String uploadAvatar(MultipartFile file, Long userId);
    // 上传用户主页背景图，返回OSS URL
    String uploadBackground(MultipartFile file, Long userId);
    // 上传帖子内容图片，返回OSS URL
    String uploadArticleImage(MultipartFile file, Long userId);
    // 上传聊天图片消息(发送方临时上传, 后续走 /message/sendImage)
    String uploadChatImage(MultipartFile file, Long userId);
    // 上传聊天表情(用户自上传到收藏库)
    String uploadChatEmoji(MultipartFile file, Long userId);
    // 上传表情商城商品图(封面 + 单图共用), 走 OSS_PATH_EMOJI_SHOP 目录
    String uploadEmojiShopImage(MultipartFile file, Long userId);

    /** 公告卡片配图：路径 forum_notice_picture/，文件名 发布者ID_公告ID_东八区时间戳 */
    String uploadNoticePicture(MultipartFile file, Long publisherUserId, Long noticeId);

    /**
     * 抽奖奖品库配图：路径 forum_db_item/forum_prize_picture/，
     * 文件名 活动ID_奖品ID_yyyyMMddHHmmss.ext（奖品库未关联活动时可传 activityId=0；新建奖品 prizeId=0）
     */
    String uploadLotteryPrizePicture(MultipartFile file, long activityId, long prizeId);

    /**
     * 抽奖活动封面：路径 forum_db_item/forum_activity_picture/，
     * 文件名 活动ID_发布者ID_yyyyMMddHHmmss.ext（新建活动可传 activityId=0）
     */
    String uploadLotteryActivityPicture(MultipartFile file, long activityId, long publisherUserId);

    /**
     * 将 AI 生图结果（https 或 data URL）转存 OSS，返回适合入库的短链接。
     */
    String uploadCompanionAiImageFromRemote(Long userId, String sourceUrl);
}
