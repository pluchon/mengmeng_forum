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

    String sendMailLoginCode(String email, String captchaTicket);

    AuthLoginResultVO loginBySms(String phoneNumber, String code, String captchaTicket, HttpServletRequest httpRequest);

    String sendSmsLoginCode(String phoneNumber, String captchaTicket);

    String sendResetCodeByMail(String email, String captchaTicket);

    void completeResetByMail(String email, String code, String newPassword, String captchaTicket);

    String sendResetCodeBySms(Long sessionUserId, String phoneNumber, String captchaTicket);

    void completeResetBySms(Long sessionUserId, String phoneNumber, String code, String newPassword, String captchaTicket);

    UserSessionVO getSessionUser(Long userId);

    UserSessionVO modifyUser(ModifyUserRequest request, Long userId);
}
