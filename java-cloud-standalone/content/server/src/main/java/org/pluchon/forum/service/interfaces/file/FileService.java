package org.pluchon.forum.service.interfaces.file;

import org.pluchon.forum.entity.vo.file.BatchImageUploadResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    // 上传帖子封面，返回OSS URL
    String uploadCoverImage(MultipartFile file, Long userId);
    // 上传用户头像，返回OSS URL
    String uploadAvatar(MultipartFile file, Long userId);
    // 上传用户主页背景图，返回OSS URL
    String uploadBackground(MultipartFile file, Long userId);
    // 上传收藏夹封面，返回OSS URL
    String uploadFavoriteFolderCover(MultipartFile file, Long userId);
    // 上传帖子内容图片，返回OSS URL
    String uploadArticleImage(MultipartFile file, Long userId);
    // 批量上传帖子内容图片，允许部分成功，最多9张
    BatchImageUploadResultVO uploadArticleImages(MultipartFile[] files, Long userId);
    // 上传帖子视频 单个 ，返回OSS URL；大于100MB会走FFmpeg压缩
    String uploadArticleVideo(MultipartFile file, Long userId);
    // 上传聊天图片消息 发送方临时上传, 后续走 /message/sendImage
    String uploadChatImage(MultipartFile file, Long userId);
    // 批量上传聊天图片，允许部分成功，最多9张
    BatchImageUploadResultVO uploadChatImages(MultipartFile[] files, Long userId);
    // 上传聊天表情 用户自上传到收藏库
    String uploadChatEmoji(MultipartFile file, Long userId);
    // 批量上传聊天表情，允许部分成功，最多9张；成功后再逐张 favorite
    BatchImageUploadResultVO uploadChatEmojis(MultipartFile[] files, Long userId);
    // 上传表情商城商品图 封面 + 单图共用 , 走 OSS_PATH_EMOJI_SHOP 目录
    String uploadEmojiShopImage(MultipartFile file, Long userId);
    // 批量上传表情商城包内图，允许部分成功，最多9张
    BatchImageUploadResultVO uploadEmojiShopImages(MultipartFile[] files, Long userId);

    // 将 AI 生图结果 https 或 data URL 转存 OSS，返回适合入库的短链接
    String uploadAiGeneratedImageFromRemote(Long userId, String sourceUrl, String ossPath, String baseName);

    // 将帖子 MP4 转码为单码率 HLS 并上传 OSS，返回 index.m3u8 公网 URL
    String transcodeArticleVideoToHls(Long articleId, String sourceVideoUrl);
}
