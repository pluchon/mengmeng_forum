package org.example.forumdemo.service.interfaces.user;

/**
 * 找回 / 重置密码
 * 单独成一个 Service 是为了：
 *   1. 避免 UserService 反向依赖 MailCodeService / SMSCodeService 造成的循环依赖隐患
 *   2. "找回密码" 只是一种特殊的 "凭验证码改密码"，不属于 UserService 的核心账户管理职责
 */
public interface PasswordResetService {

    // 通过邮箱重置密码（验证码已由 MailController 阶段发送）
    void resetByMail(String email, String code, String newPassword);

    // 通过手机号重置密码（验证码已由 SMSController 阶段发送）
    void resetBySms(String phoneNumber, String code, String newPassword);

    // 已登录用户通过当前绑定手机号重置密码，前端只需要提交验证码与新密码
    void resetByBoundSms(Long userId, String code, String newPassword);
}
