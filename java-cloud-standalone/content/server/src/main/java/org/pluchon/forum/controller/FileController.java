package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.api.content.AiGeneratedImageUploadRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.service.interfaces.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件模块", description = "处理文件上传等接口")
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileService fileService;

    @Operation(summary = "上传帖子封面", description = "上传单张图片作为帖子封面，并返回OSS的URL")
    @PostMapping("/uploadCover")
    public Result<String> uploadCover(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        String url = fileService.uploadCoverImage(file, loginUser.getId());
        return Result.successData(url);
    }

    @Operation(summary = "上传用户头像", description = "上传图片到OSS返回URL，再调用 /user/updateAvatarUrl 写入数据库")
    @PostMapping("/uploadAvatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadAvatar(file, loginUser.getId()));
    }

    @Operation(summary = "上传用户主页背景图", description = "上传图片到OSS返回URL，再调用 /user/updateBackgroundUrl 写入数据库")
    @PostMapping("/uploadBackground")
    public Result<String> uploadBackground(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadBackground(file, loginUser.getId()));
    }

    @Operation(summary = "上传帖子内容图片", description = "上传图片到OSS返回URL，可直接嵌入富文本编辑器")
    @PostMapping("/uploadArticleImage")
    public Result<String> uploadArticleImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadArticleImage(file, loginUser.getId()));
    }

    @Operation(summary = "上传帖子视频", description = "上传单个视频到 OSS；大于100MB会触发压缩；返回 URL 后再调 /article/setArticleVideo 落库")
    @PostMapping("/uploadArticleVideo")
    public Result<String> uploadArticleVideo(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null){
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadArticleVideo(file, loginUser.getId()));
    }

    @Operation(summary = "上传聊天图片消息", description = "发送方先上传图片获得OSS URL，再调 /message/sendImage 发送")
    @PostMapping("/uploadChatImage")
    public Result<String> uploadChatImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadChatImage(file, loginUser.getId()));
    }

    @Operation(summary = "上传聊天表情", description = "用户自上传表情到收藏库, 返回URL后再调 /message/emoji/favorite 入库")
    @PostMapping("/uploadChatEmoji")
    public Result<String> uploadChatEmoji(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadChatEmoji(file, loginUser.getId()));
    }

    @Operation(summary = "上传表情商城商品图", description = "封面 + 包内单图共用此接口, 返回URL后再调 /shop/createShop 落库")
    @PostMapping("/uploadEmojiShopImage")
    public Result<String> uploadEmojiShopImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadEmojiShopImage(file, loginUser.getId()));
    }

    @Operation(summary = "上传公告中心卡片配图", description = "落库路径 forum_notice_picture/，文件名：发布者ID_公告ID_东八区时间；新建公告传 noticeId=0")
    @PostMapping("/uploadNoticePicture")
    public Result<String> uploadNoticePicture(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "noticeId", required = false) Long noticeId,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        long nid = noticeId != null ? noticeId : 0L;
        return Result.successData(fileService.uploadNoticePicture(file, loginUser.getId(), nid));
    }

    @Operation(summary = "上传抽奖奖品库配图", description = "路径 forum_db_item/forum_prize_picture/；文件名 活动ID_奖品ID_时间戳.ext。奖品库未绑活动传 activityId=0；新建未落库传 prizeId=0")
    @PostMapping("/uploadLotteryPrizePicture")
    public Result<String> uploadLotteryPrizePicture(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "activityId", required = false) Long activityId,
            @RequestParam(value = "prizeId", required = false) Long prizeId,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        long aid = activityId != null ? activityId : 0L;
        long pid = prizeId != null ? prizeId : 0L;
        return Result.successData(fileService.uploadLotteryPrizePicture(file, aid, pid));
    }

    @Operation(summary = "上传抽奖活动封面", description = "路径 forum_db_item/forum_activity_picture/；新建活动传 activityId=0")
    @PostMapping("/uploadLotteryActivityPicture")
    public Result<String> uploadLotteryActivityPicture(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "activityId", required = false) Long activityId,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        long aid = activityId != null ? activityId : 0L;
        return Result.successData(fileService.uploadLotteryActivityPicture(file, aid, loginUser.getId()));
    }

    /** 内部：AI 域生图结果转存 OSS（ai → content） */
    @PostMapping("/internal/upload-ai-generated")
    public String uploadAiGeneratedInternal(@RequestBody AiGeneratedImageUploadRequest request) {
        return fileService.uploadAiGeneratedImageFromRemote(
                request.getUserId(),
                request.getSourceUrl(),
                request.getOssPath(),
                request.getBaseName());
    }
}
