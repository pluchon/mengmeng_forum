package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.pluchon.forum.common.captcha.CaptchaTicketPurpose;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.user.ModifyUserRequest;
import org.pluchon.forum.entity.dto.user.ProfileChangeRequest;
import org.pluchon.forum.entity.dto.user.UserLoginRequest;
import org.pluchon.forum.entity.dto.user.UserResigterRequest;
import org.pluchon.forum.entity.vo.user.AuthLoginResultVO;
import org.pluchon.forum.entity.vo.user.UserFollowListItemVO;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;
import org.pluchon.forum.entity.vo.user.UserLoginLogVO;
import org.pluchon.forum.entity.vo.user.UserSecurityAssessmentVO;
import org.pluchon.forum.entity.vo.user.UserSessionVO;
import org.pluchon.forum.entity.vo.user.ProfileChangeStatusVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.cloud.AuthWebSocketInternalFeignClient;
import org.pluchon.forum.service.interfaces.captcha.CaptchaTicketService;
import org.pluchon.forum.service.interfaces.user.UserAuthFlowService;
import org.pluchon.forum.service.interfaces.user.UserFollowService;
import org.pluchon.forum.service.interfaces.user.UserLoginLogService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.pluchon.forum.service.interfaces.user.UserProfileChangeService;
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
    private UserAuthFlowService userAuthFlowService;

    @Autowired
    private CaptchaTicketService captchaTicketService;

    @Autowired
    private AuthWebSocketInternalFeignClient authWebSocketInternalFeignClient;

    @Autowired
    private UserLoginLogService userLoginLogService;

    @Autowired
    private UserFollowService userFollowService;

    @Autowired
    private UserProfileChangeService userProfileChangeService;

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
    public Result<UserSessionVO> login(@Valid @RequestBody UserLoginRequest userLoginRequest,
                                       @RequestHeader(value = "X-Captcha-Ticket", required = false) String captchaTicket,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        AuthLoginResultVO login = userAuthFlowService.loginByPassword(userLoginRequest, captchaTicket, request);
        applyAuthHeaders(response, login);
        return Result.success(login.getUser());
    }

    @Operation(summary = "获取当前登录用户信息", description = "从 Session 获取用户ID后查库返回")
    @GetMapping("/getUserByIdForLogin")
    public Result<UserSessionVO> getUserByIdForLogin(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userAuthFlowService.getSessionUser(sessionUser.getId()));
    }

    @Operation(summary = "退出登录", description = "递增当前账号 token 版本，使当前 JWT 立即失效")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        userService.logout(sessionUser.getId());
        return Result.success("退出成功");
    }

    @Operation(summary = "更新用户信息", description = "不包括密码、头像、背景图等需要单独流程的字段")
    @PutMapping("/modifyUser")
    public Result<UserSessionVO> modifyUser(@RequestBody ModifyUserRequest modifyUserRequest, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userAuthFlowService.modifyUser(modifyUserRequest, sessionUser.getId()));
    }

    @Operation(summary = "更新用户头像 URL", description = "前端先调 /file/uploadAvatar 拿到 URL，再调用此接口写入数据库")
    @PostMapping("/updateAvatarUrl")
    public Result<String> updateAvatarUrl(@RequestParam String url, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        userService.updateAvatarUrl(sessionUser.getId(), url);
        return Result.success("头像更新成功");
    }

    @Operation(summary = "更新用户背景图 URL", description = "前端先调 /file/uploadBackground 拿到 URL，再调用此接口写入数据库")
    @PostMapping("/updateBackgroundUrl")
    public Result<String> updateBackgroundUrl(@RequestParam String url, HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
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
            return Result.success(userAuthFlowService.sendResetCodeByMail(email, captchaTicket));
        }
        userAuthFlowService.completeResetByMail(email, code, newPassword, captchaTicket);
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
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long sessionUserId = sessionUser != null ? sessionUser.getId() : null;
        if (!StringUtils.hasText(code)) {
            return Result.success(userAuthFlowService.sendResetCodeBySms(sessionUserId, phoneNumber, captchaTicket));
        }
        userAuthFlowService.completeResetBySms(sessionUserId, phoneNumber, code, newPassword, captchaTicket);
        return Result.success("密码重置成功！");
    }

    @Operation(summary = "查询用户是否在线", description = "基于 WebSocket 连接状态")
    @GetMapping("/isOnline")
    public Result<Boolean> isOnline(@RequestParam Long userId) {
        if (userId == null) {
            return Result.successData(false);
        }
        return Result.successData(Boolean.TRUE.equals(authWebSocketInternalFeignClient.isOnline(userId)));
    }

    @Operation(summary = "登录日志", description = "查询当前用户最近登录记录")
    @GetMapping("/loginLogs")
    public Result<List<UserLoginLogVO>> loginLogs(
            @RequestParam(defaultValue = "20") Integer limit,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userLoginLogService.listRecent(sessionUser.getId(), limit == null ? 20 : limit));
    }

    /** 提交昵称或个人简介审核 */
    @PostMapping("/profile/change-request")
    public Result<ProfileChangeStatusVO> submitProfileChange(
            @Valid @RequestBody ProfileChangeRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userProfileChangeService.submit(sessionUser.getId(), request));
    }

    /** 查询昵称或个人简介的最新审核状态 */
    @GetMapping("/profile/change-request/status")
    public Result<ProfileChangeStatusVO> profileChangeStatus(
            @RequestParam String fieldType,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userProfileChangeService.latest(sessionUser.getId(), fieldType));
    }

    /** 重新评估当前账号的密码与绑定信息 */
    @Operation(summary = "账号安全评估", description = "基于绑定完备度与近期登录日志风险实时计算")
    @GetMapping("/securityAssessment")
    public Result<UserSecurityAssessmentVO> securityAssessment(HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(userService.assessSecurity(sessionUser.getId()));
    }

    @Operation(summary = "关注用户")
    @PutMapping("/followUser")
    public Result<String> followUser(@RequestParam Long followeeId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        userFollowService.follow(loginUser.getId(), followeeId);
        return Result.success("关注成功");
    }

    @Operation(summary = "取消关注")
    @PutMapping("/unfollowUser")
    public Result<String> unfollowUser(@RequestParam Long followeeId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        userFollowService.unfollow(loginUser.getId(), followeeId);
        return Result.success("已取消关注");
    }

    @Operation(summary = "关注统计", description = "返回关注数、粉丝数；登录且查看他人主页时附带 isFollowing")
    @GetMapping("/followStats")
    public Result<UserFollowStatsVO> followStats(@RequestParam Long userId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long viewerId = loginUser != null ? loginUser.getId() : null;
        return Result.success(userFollowService.getStats(userId, viewerId));
    }

    @Operation(summary = "本月新增粉丝", description = "当前登录用户从本月第一天至今新增的粉丝数")
    @GetMapping("/creator/monthly-new-followers")
    public Result<Long> currentMonthNewFollowers(HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(userFollowService.getCurrentMonthNewFollowerCount(loginUser.getId()));
    }

    @Operation(summary = "我关注的用户ID列表", description = "用于首页热帖等场景标注「你的关注」")
    @GetMapping("/followingIds")
    public Result<List<Long>> followingIds(HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        Set<Long> ids = userFollowService.listFollowingIds(loginUser.getId());
        return Result.success(ids.stream().sorted().toList());
    }

    @Operation(summary = "关注列表", description = "某用户关注的人；按关注时间降序；keyword 仅按昵称模糊搜索")
    @GetMapping("/followingList")
    public Result<PageResult<UserFollowListItemVO>> followingList(
            @RequestParam Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long viewerId = loginUser != null ? loginUser.getId() : null;
        return Result.success(userFollowService.listFollowingPage(userId, viewerId, keyword, pageNum, pageSize));
    }

    @Operation(summary = "粉丝列表", description = "关注某用户的人；按关注时间降序；keyword 仅按昵称模糊搜索")
    @GetMapping("/followerList")
    public Result<PageResult<UserFollowListItemVO>> followerList(
            @RequestParam Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long viewerId = loginUser != null ? loginUser.getId() : null;
        return Result.success(userFollowService.listFollowersPage(userId, viewerId, keyword, pageNum, pageSize));
    }

    private static void applyAuthHeaders(HttpServletResponse response, AuthLoginResultVO login) {
        if (login == null || login.getToken() == null) {
            return;
        }
        response.setHeader(Constant.JWT_NAME, login.getToken());
        response.setHeader(Constant.ACCESS_CONTROL_EXPOSE_HEADERS, Constant.JWT_NAME);
    }
}
