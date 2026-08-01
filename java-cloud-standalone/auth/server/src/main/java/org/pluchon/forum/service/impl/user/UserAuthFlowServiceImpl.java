package org.pluchon.forum.service.impl.user;

import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.captcha.CaptchaTicketPurpose;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.converter.UserConverter;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.user.ModifyUserRequest;
import org.pluchon.forum.entity.dto.user.UserLoginRequest;
import org.pluchon.forum.entity.vo.user.AuthLoginResultVO;
import org.pluchon.forum.entity.vo.user.UserSessionVO;
import org.pluchon.forum.common.exception.ApplicationException;
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

    @Override
    public AuthLoginResultVO loginByPassword(UserLoginRequest request, String captchaTicket, HttpServletRequest httpRequest) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.USER_LOGIN);
        User user = userService.login(request);
        userLoginLogService.recordSuccess(user.getId(), "password", httpRequest);
        return UserConverter.toAuthLoginResult(user);
    }

    @Override
    public AuthLoginResultVO loginByMail(String email, String code, String captchaTicket, HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.MAIL_LOGIN);
        User user = mailCodeService.loginByMail(email, code);
        userLoginLogService.recordSuccess(user.getId(), "mail", httpRequest);
        return UserConverter.toAuthLoginResult(user);
    }

    @Override
    public String sendMailLoginCode(String email, String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.MAIL_SEND);
        mailCodeService.send(email);
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
        return UserConverter.toAuthLoginResult(user);
    }

    @Override
    public String sendSmsLoginCode(String phoneNumber, String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.SMS_SEND);
        smsCodeService.send(phoneNumber);
        return "验证码已发送~";
    }

    @Override
    public String sendResetCodeByMail(String email, String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SEND);
        mailCodeService.sendForReset(email);
        return "重置密码验证码已发送至您的邮箱~";
    }

    @Override
    public void completeResetByMail(String email, String code, String newPassword, String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SUBMIT);
        passwordResetService.resetByMail(email, code, newPassword);
    }

    @Override
    public String sendResetCodeBySms(Long sessionUserId, String phoneNumber, String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SEND);
        boolean useBoundPhone = sessionUserId != null
                && (!StringUtils.hasText(phoneNumber) || phoneNumber.contains("*"));
        if (useBoundPhone) {
            smsCodeService.sendForResetBound(sessionUserId);
        } else {
            smsCodeService.sendForReset(phoneNumber);
        }
        return "重置密码验证码已发送~";
    }

    @Override
    public void completeResetBySms(Long sessionUserId, String phoneNumber, String code, String newPassword,
                                   String captchaTicket) {
        requireCaptchaTicket(captchaTicket, CaptchaTicketPurpose.RESET_SUBMIT);
        boolean useBoundPhone = sessionUserId != null
                && (!StringUtils.hasText(phoneNumber) || phoneNumber.contains("*"));
        if (useBoundPhone) {
            passwordResetService.resetByBoundSms(sessionUserId, code, newPassword);
        } else {
            passwordResetService.resetBySms(phoneNumber, code, newPassword);
        }
    }

    @Override
    public UserSessionVO getSessionUser(Long userId) {
        return UserConverter.toSessionVO(userService.getUserInfoById(userId));
    }

    @Override
    public UserSessionVO modifyUser(ModifyUserRequest request, Long userId) {
        return UserConverter.toSessionVO(userService.modifyUser(request, userId));
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
