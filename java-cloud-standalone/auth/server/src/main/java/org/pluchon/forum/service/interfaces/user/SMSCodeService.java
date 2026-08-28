package org.pluchon.forum.service.interfaces.user;

import org.pluchon.forum.entity.db.User;

// 短信验证服务
public interface SMSCodeService {
    // 发送验证码 短信登录，号码未注册时直接拒绝，不浪费短信
    void sendForLogin(String phoneNumber, String clientIp);

    // 发送验证码 绑定/修改手机号 ，需要知道当前用户以拦截重复绑定自己的手机号
    void sendForBind(String phoneNumber, Long userId, String clientIp);

    // 发送验证码 重置密码专用，号码未注册时直接拒绝
    void sendForReset(String phoneNumber, String clientIp);

    // 已登录用户通过当前绑定手机号重置密码时，由后端解密手机号后发送验证码
    void sendForResetBound(Long userId, String clientIp);

    // 原子消费绑定/登录验证码，成功返回 true
    boolean consumeVerificationCode(String phoneNumber, String code);

    // 原子消费重置密码验证码，成功返回 true
    boolean consumeResetCode(String phoneNumber, String code);

    // 短信验证码登录
    User loginBySms(String phoneNumber, String code);

    // 验证码校验通过后绑定/修改手机号
    // 已经绑过手机号的账号属于"改绑"，必须先校验当前密码，防止会话被盗后改绑接管账号
    void verifyAndBind(String phoneNumber, String code, Long userId, String currentPassword);
}
