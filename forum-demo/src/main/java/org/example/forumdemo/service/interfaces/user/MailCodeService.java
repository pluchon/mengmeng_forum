package org.example.forumdemo.service.interfaces.user;

import org.example.forumdemo.entity.db.User;

/**
 * 邮箱验证服务
 */
public interface MailCodeService {
    // 发送邮箱验证码 (通用/绑定)
    void send(String email);

    // 发送邮箱验证码 (重置密码专用)
    void sendForReset(String email);

    // 获取验证码 (重置密码专用)
    String getForReset(String email);

    // 邮箱登录
    User loginByMail(String email, String code);

    // 验证码校验通过后绑定/修改邮箱
    void verifyAndBind(String email, String code, Long userId);
}
