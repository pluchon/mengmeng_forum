package org.pluchon.forum.service.interfaces.user;

import org.pluchon.forum.entity.db.User;

// 邮箱验证服务
public interface MailCodeService {
    // 发送邮箱验证码 通用/绑定
    void send(String email);

    // 发送邮箱验证码 重置密码专用
    void sendForReset(String email);

    // 获取验证码 重置密码专用，仅开发排查；生产流程应使用 consumeResetCode
    String getForReset(String email);

    // 原子消费绑定/登录验证码，成功返回 true
    boolean consumeVerificationCode(String email, String code);

    // 原子消费重置密码验证码，成功返回 true
    boolean consumeResetCode(String email, String code);

    // 邮箱登录
    User loginByMail(String email, String code);

    // 验证码校验通过后绑定/修改邮箱
    void verifyAndBind(String email, String code, Long userId);
}
