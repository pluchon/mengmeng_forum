package org.example.forumdemo.service.impl.remote;

import org.example.forum.api.content.AiGeneratedImageUploadRequest;
import org.example.forum.cloud.feign.FileInternalFeignClient;
import org.example.forumdemo.service.interfaces.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// 非 content 域通过 Feign 调用文件内部接口（目前仅 AI 生图转存）
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'content'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class FileRemoteService implements FileService {

    @Autowired
    private FileInternalFeignClient fileInternalFeignClient;

    @Override
    public String uploadAiGeneratedImageFromRemote(Long userId, String sourceUrl, String ossPath, String baseName) {
        return fileInternalFeignClient.uploadAiGeneratedImage(
                new AiGeneratedImageUploadRequest(userId, sourceUrl, ossPath, baseName));
    }

    @Override
    public String uploadCoverImage(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传封面");
    }

    @Override
    public String uploadAvatar(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传头像");
    }

    @Override
    public String uploadBackground(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传背景图");
    }

    @Override
    public String uploadArticleImage(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传帖子图片");
    }

    @Override
    public String uploadArticleVideo(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传帖子视频");
    }

    @Override
    public String uploadChatImage(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传聊天图片");
    }

    @Override
    public String uploadChatEmoji(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传聊天表情");
    }

    @Override
    public String uploadEmojiShopImage(MultipartFile file, Long userId) {
        throw new UnsupportedOperationException("请走 content 服务上传表情商城图");
    }

    @Override
    public String uploadNoticePicture(MultipartFile file, Long publisherUserId, Long noticeId) {
        throw new UnsupportedOperationException("请走 content 服务上传公告配图");
    }

    @Override
    public String uploadLotteryPrizePicture(MultipartFile file, long activityId, long prizeId) {
        throw new UnsupportedOperationException("请走 content 服务上传奖品图");
    }

    @Override
    public String uploadLotteryActivityPicture(MultipartFile file, long activityId, long publisherUserId) {
        throw new UnsupportedOperationException("请走 content 服务上传活动封面");
    }
}
