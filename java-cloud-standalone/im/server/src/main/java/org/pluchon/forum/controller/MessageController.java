package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.message.FavoriteEmojiRequest;
import org.pluchon.forum.entity.dto.message.MessageReplyRequest;
import org.pluchon.forum.entity.dto.message.MessageSessionPinRequest;
import org.pluchon.forum.entity.dto.message.MessageSessionVisibilityRequest;
import org.pluchon.forum.entity.dto.message.SendAlbumMessageRequest;
import org.pluchon.forum.entity.dto.message.SendImageMessageRequest;
import org.pluchon.forum.entity.dto.message.SendMessageRequest;
import org.pluchon.forum.entity.dto.message.ChatMessageReportRequest;
import org.pluchon.forum.entity.vo.message.MessageDetailResponse;
import org.pluchon.forum.entity.vo.message.MessageSessionResponse;
import org.pluchon.forum.entity.vo.message.MessageSessionSearchResponse;
import org.pluchon.forum.entity.vo.message.MessageVO;
import org.pluchon.forum.entity.vo.message.UserChatEmojiResponse;
import org.pluchon.forum.entity.vo.message.ChatMessageReportVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.message.MessageService;
import org.pluchon.forum.service.interfaces.message.ChatMessageReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "私信模块", description = "私信的增删改查接口")
@RestController
@RequestMapping("/message")
public class MessageController {
    @Autowired
    private MessageService messageService;

    @Autowired
    private ChatMessageReportService chatMessageReportService;

    /** 举报私信或群聊文本消息 */
    @PostMapping("/report")
    public Result<ChatMessageReportVO> reportMessage(
            @Valid @RequestBody ChatMessageReportRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(chatMessageReportService.report(sessionUser.getId(), request));
    }

    @Operation(summary = "发送私信(纯文本)", description = "传入接收者ID以及文本内容; 图片消息请走 /message/sendImage")
    @PostMapping("/sendMessage")
    public Result<MessageVO> sendMessage(@RequestBody SendMessageRequest sendMessageRequest, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.send(sendMessageRequest, sessionUser.getId()));
    }

    @Operation(summary = "发送图片/GIF 私信",
            description = "前端先调 /file/uploadChatImage 上传图片拿到 OSS URL, 再调本接口落库 + 通知; 图片消息不允许携带文字")
    @PostMapping("/sendImage")
    public Result<MessageVO> sendImage(@RequestBody SendImageMessageRequest req, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.sendImage(req, sessionUser.getId()));
    }

    @Operation(summary = "收藏表情",
            description = "两种来源: (1)自上传(originMessageId 留空, mediaUrl 来自 /file/uploadChatEmoji); (2)收藏聊天图(传 originMessageId 与消息内 mediaUrl, URL 可为临时聊天图或表情库路径)")
    @PostMapping("/emoji/favorite")
    public Result<UserChatEmojiResponse> favoriteEmoji(@RequestBody FavoriteEmojiRequest req, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.favoriteEmoji(req, sessionUser.getId()));
    }

    @Operation(summary = "取消收藏表情", description = "仅可删除属于自己的表情记录, 软删")
    @DeleteMapping("/emoji/{emojiId}")
    public Result<String> cancelFavoriteEmoji(@PathVariable Long emojiId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.cancelFavoriteEmoji(emojiId, sessionUser.getId());
        return Result.success("已取消收藏");
    }

    @Operation(summary = "我的表情分页列表", description = "source=uploaded 查询我的上传，source=favorite 查询聊天收藏；按创建时间升序")
    @GetMapping("/emoji/list")
    public Result<PageResult<UserChatEmojiResponse>> queryEmojiList(
            @RequestParam String source,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "8") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.queryEmojiList(sessionUser.getId(), source, pageNum, pageSize));
    }

    // 发送一至十张图片组成的私信图集，可附带说明文字
    @Operation(summary = "发送图集私信",
            description = "前端先逐张调用 /file/uploadChatImage，再一次提交最多十张图片及可选说明文字")
    @PostMapping("/sendAlbum")
    public Result<MessageVO> sendAlbum(@Valid @RequestBody SendAlbumMessageRequest req,
                                       HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.sendAlbum(req, sessionUser.getId()));
    }

    @Operation(summary = "查询私信未读数量", description = "获取当前登录的用户ID")
    @GetMapping("/getUnReadMessage")
    public Result<Long> getUnReadMessage(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.selectMessageUnRead(sessionUser.getId()));
    }

    @Operation(summary = "安全更新单条私信状态",
            description = "主要用于接收方标记单条消息已读(status=1); status=2 会转交撤回逻辑并校验发送方和2分钟窗口; 不允许改回未读")
    @PutMapping("/updateMessageStatusByMessageId")
    public Result<String> updateMessageStatusByMessageId(Long messageId, Byte status, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.updateMessageStatusByMessageId(messageId, status, sessionUser.getId());
        return Result.success("站内信状态更新成功");
    }

    @Operation(summary = "撤回私信", description = "仅发送者可撤回，且必须在2分钟内")
    @PutMapping("/recallMessage")
    public Result<String> recallMessage(Long messageId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.recallMessage(messageId, sessionUser.getId());
        return Result.success("私信已撤回");
    }

    @Operation(summary = "回复站内信", description = "传入回复的内容以及回复给谁")
    @PostMapping("/replyMessage")
    public Result<String> replyMessage(@RequestBody MessageReplyRequest messageReplyRequest, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.replyMessage(messageReplyRequest, sessionUser.getId());
        return Result.success("回复站内信成功");
    }

    @Operation(summary = "获取当前登录用户的站内信会话列表(分页)", description = "传入分页参数")
    @GetMapping("/queryMessageSessionWithPage")
    public Result<PageResult<MessageSessionResponse>> queryMessageSessionWithPage(
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.queryMessageSessionWithPage(sessionUser.getId(), pageNum, pageSize));
    }

    /** 搜索当前用户私信中的文本会话 */
    @GetMapping("/searchSessions")
    public Result<PageResult<MessageSessionSearchResponse>> searchMessageSessions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.searchMessageSessions(sessionUser.getId(), keyword, pageNum, pageSize));
    }

    /** 隐藏当前用户视角下的私信会话 */
    @PostMapping("/session/hide")
    public Result<Void> hideMessageSession(@Valid @RequestBody MessageSessionVisibilityRequest request,
                                           HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.hideMessageSession(sessionUser.getId(), request.getPeerUserId());
        return Result.success();
    }

    /** 恢复当前用户视角下的私信会话 */
    @PostMapping("/session/restore")
    public Result<Void> restoreMessageSession(@Valid @RequestBody MessageSessionVisibilityRequest request,
                                              HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.restoreMessageSession(sessionUser.getId(), request.getPeerUserId());
        return Result.success();
    }

    /** 置顶或取消置顶私信会话，最多置顶十个 */
    @PostMapping("/session/pin")
    public Result<Void> pinMessageSession(@Valid @RequestBody MessageSessionPinRequest request,
                                          HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.pinMessageSession(sessionUser.getId(), request.getPeerUserId(),
                Boolean.TRUE.equals(request.getPinned()));
        return Result.success();
    }

    /** 查询当前用户主动隐藏的私信会话 */
    @GetMapping("/session/hidden")
    public Result<PageResult<MessageSessionResponse>> queryHiddenMessageSessions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.queryHiddenMessageSessions(sessionUser.getId(), pageNum, pageSize));
    }

    @Operation(summary = "获取和特定用户的会话记录(分页)", description = "传入对方用户ID和分页参数")
    @GetMapping("/queryMessageDetailWithPage")
    public Result<PageResult<MessageDetailResponse>> queryMessageDetailWithPage(Long receiveId,
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.queryMessageDetailWithPage(sessionUser.getId(), receiveId, pageNum, pageSize));
    }

    @Operation(summary = "按消息ID查询单条私信", description = "WebSocket 收到 dbMessageId 后用于实时追加聊天气泡; 仅收发双方可见")
    @GetMapping("/queryMessageDetailById")
    public Result<MessageDetailResponse> queryMessageDetailById(Long messageId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(messageService.queryMessageDetailById(messageId, sessionUser.getId()));
    }

    @Operation(summary = "进入会话时标记所有未读为已读", description = "传入对方用户ID，将其发给我的所有未读消息标为已读")
    @PutMapping("/markAllMessageReadBySender")
    public Result<String> markAllMessageReadBySender(Long senderId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        messageService.markAllMessageReadBySender(sessionUser.getId(), senderId);
        return Result.success("已全部标为已读");
    }
}

