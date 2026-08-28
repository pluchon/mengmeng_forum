package org.pluchon.forum.service.interfaces.user;

import org.pluchon.forum.entity.db.User;

// 邮箱验证服务
public interface MailCodeService {
    // 发送邮箱验证码 邮箱登录，邮箱未注册时直接拒绝
    void sendForLogin(String email, String clientIp);

    // 发送邮箱验证码 绑定/修改邮箱，邮箱已被他人占用时直接拒绝
    void sendForBind(String email, Long userId, String clientIp);

    // 发送邮箱验证码 重置密码专用，邮箱未注册时直接拒绝
    void sendForReset(String email, String clientIp);

    // 原子消费绑定/登录验证码，成功返回 true
    boolean consumeVerificationCode(String email, String code);

    // 原子消费重置密码验证码，成功返回 true
    boolean consumeResetCode(String email, String code);

    // 邮箱登录
    User loginByMail(String email, String code);

    // 验证码校验通过后绑定/修改邮箱
    // 已经绑过邮箱的账号属于"改绑"，必须先校验当前密码，防止会话被盗后改绑接管账号
    void verifyAndBind(String email, String code, Long userId, String currentPassword);
}
