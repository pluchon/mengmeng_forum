package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.api.content.AiGeneratedImageUploadRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.file.BatchImageUploadResultVO;
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

    /** 上传收藏夹封面 */
    @Operation(summary = "上传收藏夹封面", description = "上传并审核收藏夹封面，返回OSS URL后通过收藏夹更新接口落库")
    @PostMapping("/uploadFavoriteFolderCover")
    public Result<String> uploadFavoriteFolderCover(@RequestParam("file") MultipartFile file,
                                                     HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadFavoriteFolderCover(file, loginUser.getId()));
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

    /** 批量上传帖子内容图片，允许部分成功 */
    @Operation(summary = "批量上传帖子内容图片", description = "最多9张；返回成功 URL 与失败原因（按 index）")
    @PostMapping("/uploadArticleImages")
    public Result<BatchImageUploadResultVO> uploadArticleImages(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadArticleImages(files, loginUser.getId()));
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

    /** 批量上传聊天图片，允许部分成功 */
    @Operation(summary = "批量上传聊天图片", description = "最多9张；返回成功 URL 与失败原因（按 index）；再调 sendImage / sendAlbum")
    @PostMapping("/uploadChatImages")
    public Result<BatchImageUploadResultVO> uploadChatImages(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadChatImages(files, loginUser.getId()));
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

    /** 批量上传聊天表情，允许部分成功；自上传收藏须走 emoji 目录 */
    @Operation(summary = "批量上传聊天表情", description = "最多9张；返回成功 URL 后再逐张调 /message/emoji/favorite")
    @PostMapping("/uploadChatEmojis")
    public Result<BatchImageUploadResultVO> uploadChatEmojis(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadChatEmojis(files, loginUser.getId()));
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

    /** 批量上传表情商城包内图，允许部分成功 */
    @Operation(summary = "批量上传表情商城商品图", description = "最多9张；封面仍用单张接口；包内图可分片批量")
    @PostMapping("/uploadEmojiShopImages")
    public Result<BatchImageUploadResultVO> uploadEmojiShopImages(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {
        AuthenticatedUser loginUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail("您尚未登录");
        }
        return Result.successData(fileService.uploadEmojiShopImages(files, loginUser.getId()));
    }

    /** 内部：AI 域生图结果转存 OSS ai → content */
    @PostMapping("/internal/upload-ai-generated")
    public String uploadAiGeneratedInternal(@RequestBody AiGeneratedImageUploadRequest request) {
        return fileService.uploadAiGeneratedImageFromRemote(
                request.getUserId(),
                request.getSourceUrl(),
                request.getOssPath(),
                request.getBaseName());
    }
}
