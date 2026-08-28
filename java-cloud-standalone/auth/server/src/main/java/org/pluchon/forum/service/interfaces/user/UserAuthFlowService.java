package org.pluchon.forum.service.interfaces.user;

import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.entity.dto.user.ModifyUserRequest;
import org.pluchon.forum.entity.dto.user.UserLoginRequest;
import org.pluchon.forum.entity.vo.user.AuthLoginResultVO;
import org.pluchon.forum.entity.vo.user.UserSessionVO;

// 登录、找回密码等认证流程编排
public interface UserAuthFlowService {

    AuthLoginResultVO loginByPassword(UserLoginRequest request, String captchaTicket, HttpServletRequest httpRequest);

    AuthLoginResultVO loginByMail(String email, String code, String captchaTicket, HttpServletRequest httpRequest);

    String sendMailLoginCode(String email, String captchaTicket, HttpServletRequest httpRequest);

    AuthLoginResultVO loginBySms(String phoneNumber, String code, String captchaTicket, HttpServletRequest httpRequest);

    String sendSmsLoginCode(String phoneNumber, String captchaTicket, HttpServletRequest httpRequest);

    String sendResetCodeByMail(String email, String captchaTicket, HttpServletRequest httpRequest);

    void completeResetByMail(String email, String code, String newPassword, String captchaTicket);

    // useBoundPhone=true 表示给当前登录账号已绑定的手机号发码，此时 phoneNumber 不参与判断
    String sendResetCodeBySms(Long sessionUserId, boolean useBoundPhone, String phoneNumber, String captchaTicket,
                              HttpServletRequest httpRequest);

    void completeResetBySms(Long sessionUserId, boolean useBoundPhone, String phoneNumber, String code,
                            String newPassword, String captchaTicket);

    // 已登录用户凭当前密码改密码，不走验证码
    void changePasswordByCurrent(Long userId, String currentPassword, String newPassword);

    UserSessionVO getSessionUser(Long userId);

    UserSessionVO modifyUser(ModifyUserRequest request, Long userId);
}
