package org.pluchon.forum.service.impl.user;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.auth.client.PointsInternalFeignClient;
import org.pluchon.forum.common.captcha.CaptchaTicketPurpose;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.HttpRequestUtils;
import org.pluchon.forum.converter.UserConverter;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.user.ModifyUserRequest;
import org.pluchon.forum.entity.dto.user.UserLoginRequest;
import org.pluchon.forum.entity.vo.user.AuthLoginResultVO;
import org.pluchon.forum.entity.vo.user.UserSessionVO;
import org.pluchon.forum.service.interfaces.captcha.CaptchaTicketService;
import org.pluchon.forum.service.interfaces.user.MailCodeService;
import org.pluchon.forum.service.interfaces.user.PasswordResetService;
import org.pluchon.forum.service.interfaces.user.SMSCodeService;
import org.pluchon.forum.service.interfaces.user.UserAuthFlowService;
import org.pluchon.forum.service.interfaces.user.UserLoginLogService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// 登录与找回密码流程编排
@Slf4j
@Service
public class UserAuthFlowServiceImpl implements UserAuthFlowService {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaTicketService captchaTicketService;

    @Autowired
    private UserLoginLogService userLoginLogService;

    @Autowired
    private MailCodeService mailCodeService;

    @Autowired
    private SMSCodeService smsCodeService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PointsInternalFeignClient pointsInternalFeignClient;

    @Override
    public AuthLoginResultVO loginByPassword(UserLoginRequest request, String captchaTicket, HttpServletRequest httpRequest) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.USER_LOGIN);
        User user = userService.login(request, httpRequest);
        userLoginLogService.recordSuccess(user.getId(), "password", httpRequest);
        return withPoints(UserConverter.toAuthLoginResult(user));
    }

    @Override
    public AuthLoginResultVO loginByMail(String email, String code, String captchaTicket, HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.MAIL_LOGIN);
        User user = mailCodeService.loginByMail(email, code);
        userLoginLogService.recordSuccess(user.getId(), "mail", httpRequest);
        return withPoints(UserConverter.toAuthLoginResult(user));
    }

    @Override
    public String sendMailLoginCode(String email, String captchaTicket, HttpServletRequest httpRequest) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.MAIL_SEND);
        mailCodeService.sendForLogin(email, HttpRequestUtils.resolveClientIp(httpRequest));
        return "验证码已发送至您的邮箱~";
    }

    @Override
    public AuthLoginResultVO loginBySms(String phoneNumber, String code, String captchaTicket, HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.SMS_LOGIN);
        User user = smsCodeService.loginBySms(phoneNumber, code);
        userLoginLogService.recordSuccess(user.getId(), "sms", httpRequest);
        return withPoints(UserConverter.toAuthLoginResult(user));
    }

    @Override
    public String sendSmsLoginCode(String phoneNumber, String captchaTicket, HttpServletRequest httpRequest) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.SMS_SEND);
        smsCodeService.sendForLogin(phoneNumber, HttpRequestUtils.resolveClientIp(httpRequest));
        return "验证码已发送~";
    }

    @Override
    public String sendResetCodeByMail(String email, String captchaTicket, HttpServletRequest httpRequest) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SEND);
        mailCodeService.sendForReset(email, HttpRequestUtils.resolveClientIp(httpRequest));
        return "重置密码验证码已发送至您的邮箱~";
    }

    @Override
    public void completeResetByMail(String email, String code, String newPassword, String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SUBMIT);
        passwordResetService.resetByMail(email, code, newPassword);
    }

    @Override
    public String sendResetCodeBySms(Long sessionUserId, boolean useBoundPhone, String phoneNumber,
                                     String captchaTicket, HttpServletRequest httpRequest) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SEND);
        String clientIp = HttpRequestUtils.resolveClientIp(httpRequest);
        if (useBoundPhone) {
            smsCodeService.sendForResetBound(requireSessionUser(sessionUserId), clientIp);
        } else {
            smsCodeService.sendForReset(phoneNumber, clientIp);
        }
        return "重置密码验证码已发送~";
    }

    @Override
    public void completeResetBySms(Long sessionUserId, boolean useBoundPhone, String phoneNumber, String code,
                                   String newPassword, String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SUBMIT);
        if (useBoundPhone) {
            passwordResetService.resetByBoundSms(requireSessionUser(sessionUserId), code, newPassword);
        } else {
            passwordResetService.resetBySms(phoneNumber, code, newPassword);
        }
    }

    // "用我已绑定的手机号"只有登录态才说得通，游客带这个标记进来必须挡住
    private Long requireSessionUser(Long sessionUserId) {
        if (sessionUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return sessionUserId;
    }

    @Override
    public void changePasswordByCurrent(Long userId, String currentPassword, String newPassword) {
        passwordResetService.changeByCurrentPassword(userId, currentPassword, newPassword);
    }

    @Override
    public UserSessionVO getSessionUser(Long userId) {
        return withPoints(UserConverter.toSessionVO(userService.getUserInfoById(userId)));
    }

    @Override
    public UserSessionVO modifyUser(ModifyUserRequest request, Long userId) {
        return withPoints(UserConverter.toSessionVO(userService.modifyUser(request, userId)));
    }

    private AuthLoginResultVO withPoints(AuthLoginResultVO result) {
        if (result != null && result.getUser() != null) {
            fillPoints(result.getUser());
        }
        return result;
    }

    private UserSessionVO withPoints(UserSessionVO session) {
        fillPoints(session);
        return session;
    }

    // 积分权威在 economy.points_wallet，会话展示时按需拉取
    private void fillPoints(UserSessionVO session) {
        if (session == null || session.getId() == null) {
            return;
        }
        try {
            Integer balance = pointsInternalFeignClient.getBalance(session.getId());
            session.setPoints(balance == null ? 0 : balance);
        } catch (Exception e) {
            log.warn("拉取用户积分失败 userId={}: {}", session.getId(), e.getMessage());
            if (session.getPoints() == null) {
                session.setPoints(0);
            }
        }
    }

    private void requireCaptchaTicket(String captchaTicket, String purpose) {
        if (!StringUtils.hasText(captchaTicket)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED));
        }
        if (!captchaTicketService.consume(captchaTicket, purpose)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CAPTCHA_CHECK));
        }
    }
}
