package org.example.forumdemo.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.forumdemo.common.captcha.CaptchaTicketPurpose;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.user.ModifyUserRequest;
import org.example.forumdemo.entity.dto.user.UserLoginRequest;
import org.example.forumdemo.entity.dto.user.UserResigterRequest;
import org.example.forumdemo.service.interfaces.captcha.CaptchaTicketService;
import org.example.forumdemo.service.interfaces.user.MailCodeService;
import org.example.forumdemo.service.interfaces.user.PasswordResetService;
import org.example.forumdemo.service.interfaces.user.SMSCodeService;
import org.example.forumdemo.common.utils.OnlineUserManageUtil;
import org.example.forumdemo.service.interfaces.user.UserLoginLogService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.example.forumdemo.entity.vo.user.UserLoginLogVO;
import org.example.forumdemo.entity.vo.user.UserFollowListItemVO;
import org.example.forumdemo.entity.vo.user.UserFollowStatsVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.user.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "用户模块", description = "用户的增删改查接口")
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private MailCodeService mailCodeService;

    @Autowired
    private SMSCodeService smsCodeService;

    // 防人机的门票凭据机制，防止一直被刷接口，如果不能在规定的时间内完成业务逻辑
    // 则我们自动把该票据进行作废处理
    @Autowired
    private CaptchaTicketService captchaTicketService;

    @Autowired
    private OnlineUserManageUtil onlineUserManageUtil;

    @Autowired
    private UserLoginLogService userLoginLogService;

    @Autowired
    private UserFollowService userFollowService;

    @Operation(summary = "用户注册", description = "传入用户名、密码、昵称完成注册")
    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody UserResigterRequest userResigterRequest) {
        if (!captchaTicketService.consume(userResigterRequest.getCaptchaTicket(), CaptchaTicketPurpose.REGISTER)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
        }
        userService.resigter(userResigterRequest);
        return Result.success("注册成功");
    }

    @Operation(summary = "用户登录", description = "传入用户名/邮箱、密码完成登录，登录成功后 JWT 通过 Header 返回")
    @PostMapping("/login")
    public Result<User> login(@Valid @RequestBody UserLoginRequest userLoginRequest,
                              @RequestHeader(value = "X-Captcha-Ticket", required = false) String captchaTicket,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        if (!StringUtils.hasText(captchaTicket)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
        }
        if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.USER_LOGIN)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
        }
        User user = userService.login(userLoginRequest);
        userLoginLogService.recordSuccess(user.getId(), "password", request);
        response.setHeader(Constant.JWT_NAME, user.getToken());
        response.setHeader(Constant.ACCESS_CONTROL_EXPOSE_HEADERS, Constant.JWT_NAME);
        return Result.success(user);
    }

    @Operation(summary = "获取当前登录用户信息", description = "从 Session 获取用户ID后查库返回")
    @GetMapping("/getUserByIdForLogin")
    public Result<User> getUserByIdForLogin(HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userService.getUserInfoById(sessionUser.getId()));
    }

    @Operation(summary = "更新用户信息", description = "不包括密码、头像、背景图等需要单独流程的字段")
    @PutMapping("/modifyUser")
    public Result<User> modifyUser(@RequestBody ModifyUserRequest modifyUserRequest, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userService.modifyUser(modifyUserRequest, sessionUser.getId()));
    }

    @Operation(summary = "修改密码", description = "已登录用户基于旧密码改新密码")
    @PutMapping("/modifyPassword")
    public Result<String> modifyPassword(String oldPassword, String newPassword, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        userService.updatePawssword(sessionUser.getId(), oldPassword, newPassword);
        return Result.success("密码修改成功");
    }

    @Operation(summary = "设置当前用户看板娘模型", description = "modelId 须为 forum_mascot_model 已上架记录")
    @PostMapping("/setMascotModel")
    public Result<String> setMascotModel(@RequestParam Long modelId, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (sessionUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        userService.setMascotModel(sessionUser.getId(), modelId);
        return Result.success("已更新看板娘");
    }

    @Operation(summary = "更新用户头像 URL", description = "前端先调 /file/uploadAvatar 拿到 URL，再调用此接口写入数据库")
    @PostMapping("/updateAvatarUrl")
    public Result<String> updateAvatarUrl(@RequestParam String url, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        userService.updateAvatarUrl(sessionUser.getId(), url);
        return Result.success("头像更新成功");
    }

    @Operation(summary = "更新用户背景图 URL", description = "前端先调 /file/uploadBackground 拿到 URL，再调用此接口写入数据库")
    @PostMapping("/updateBackgroundUrl")
    public Result<String> updateBackgroundUrl(@RequestParam String url, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        userService.updateBackgroundUrl(sessionUser.getId(), url);
        return Result.success("背景图更新成功");
    }

    @Operation(summary = "通过邮箱找回密码",
            description = "code 为空时向邮箱发送重置专用验证码；code 非空时连同新密码一起提交完成重置")
    @PostMapping("/findPasswordByMail")
    public Result<String> findPasswordByMail(@RequestParam String email, @RequestParam(required = false) String code,
                                             @RequestParam(required = false) String newPassword,
                                             @RequestParam(required = false) String captchaTicket) {
        if (!StringUtils.hasText(code)) {
            if (!StringUtils.hasText(captchaTicket)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
            }
            if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.RESET_SEND)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
            }
            mailCodeService.sendForReset(email);
            return Result.success("重置密码验证码已发送至您的邮箱~");
        }
        if (!StringUtils.hasText(captchaTicket)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
        }
        if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.RESET_SUBMIT)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
        }
        passwordResetService.resetByMail(email, code, newPassword);
        return Result.success("密码重置成功！");
    }

    @Operation(summary = "通过手机号找回密码",
            description = "code 为空时向手机号发送重置专用验证码；code 非空时连同新密码一起提交完成重置")
    @PostMapping("/findPasswordBySms")
    public Result<String> findPasswordBySms(@RequestParam(required = false) String phoneNumber,
                                            @RequestParam(required = false) String code,
                                            @RequestParam(required = false) String newPassword,
                                            @RequestParam(required = false) String captchaTicket,
                                            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        // 区分登录与未登录用户的手机号形式
        boolean useBoundPhone = sessionUser != null && (!StringUtils.hasText(phoneNumber) || phoneNumber.contains("*"));
        if (!StringUtils.hasText(code)) {
            if (!StringUtils.hasText(captchaTicket)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
            }
            if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.RESET_SEND)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
            }
            if (useBoundPhone) {
                smsCodeService.sendForResetBound(sessionUser.getId());
            } else {
                smsCodeService.sendForReset(phoneNumber);
            }
            return Result.success("重置密码验证码已发送~");
        }
        if (!StringUtils.hasText(captchaTicket)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
        }
        if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.RESET_SUBMIT)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
        }
        if (useBoundPhone) {
            passwordResetService.resetByBoundSms(sessionUser.getId(), code, newPassword);
        } else {
            passwordResetService.resetBySms(phoneNumber, code, newPassword);
        }
        return Result.success("密码重置成功！");
    }

    @Operation(summary = "查询用户是否在线", description = "基于 WebSocket 连接状态")
    @GetMapping("/isOnline")
    public Result<Boolean> isOnline(@RequestParam Long userId) {
        if (userId == null) {
            return Result.successData(false);
        }
        return Result.successData(onlineUserManageUtil.isOnline(userId));
    }

    @Operation(summary = "登录日志", description = "查询当前用户最近登录记录")
    @GetMapping("/loginLogs")
    public Result<List<UserLoginLogVO>> loginLogs(
            @RequestParam(defaultValue = "20") Integer limit,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userLoginLogService.listRecent(sessionUser.getId(), limit == null ? 20 : limit));
    }

    @Operation(summary = "关注用户")
    @PutMapping("/followUser")
    public Result<String> followUser(@RequestParam Long followeeId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        userFollowService.follow(loginUser.getId(), followeeId);
        return Result.success("关注成功");
    }

    @Operation(summary = "取消关注")
    @PutMapping("/unfollowUser")
    public Result<String> unfollowUser(@RequestParam Long followeeId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        userFollowService.unfollow(loginUser.getId(), followeeId);
        return Result.success("已取消关注");
    }

    @Operation(summary = "关注统计", description = "返回关注数、粉丝数；登录且查看他人主页时附带 isFollowing")
    @GetMapping("/followStats")
    public Result<UserFollowStatsVO> followStats(@RequestParam Long userId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long viewerId = loginUser != null ? loginUser.getId() : null;
        return Result.success(userFollowService.getStats(userId, viewerId));
    }

    @Operation(summary = "我关注的用户ID列表", description = "用于首页热帖等场景标注「你的关注」")
    @GetMapping("/followingIds")
    public Result<List<Long>> followingIds(HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        Set<Long> ids = userFollowService.listFollowingIds(loginUser.getId());
        return Result.success(ids.stream().sorted().toList());
    }

    @Operation(summary = "关注列表", description = "某用户关注的人；按关注时间降序；keyword 仅在关注范围内按用户名模糊搜")
    @GetMapping("/followingList")
    public Result<PageResult<UserFollowListItemVO>> followingList(
            @RequestParam Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long viewerId = loginUser != null ? loginUser.getId() : null;
        return Result.success(userFollowService.listFollowingPage(userId, viewerId, keyword, pageNum, pageSize));
    }

    @Operation(summary = "粉丝列表", description = "关注某用户的人；按关注时间降序；keyword 仅在粉丝范围内按用户名模糊搜")
    @GetMapping("/followerList")
    public Result<PageResult<UserFollowListItemVO>> followerList(
            @RequestParam Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long viewerId = loginUser != null ? loginUser.getId() : null;
        return Result.success(userFollowService.listFollowersPage(userId, viewerId, keyword, pageNum, pageSize));
    }
}
