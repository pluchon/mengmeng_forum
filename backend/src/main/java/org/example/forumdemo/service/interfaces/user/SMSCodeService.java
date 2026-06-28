package org.example.forumdemo.service.interfaces.user;

import org.example.forumdemo.entity.db.User;

// 短信验证服务
public interface SMSCodeService {
    // 发送验证码 (通用/绑定)
    void send(String phoneNumber);

    // 发送验证码 (绑定/修改手机号)，需要知道当前用户以拦截重复绑定自己的手机号
    void sendForBind(String phoneNumber, Long userId);

    // 发送验证码 (重置密码专用)
    void sendForReset(String phoneNumber);

    // 已登录用户通过当前绑定手机号重置密码时，由后端解密手机号后发送验证码
    void sendForResetBound(Long userId);

    // 获取验证码 (重置密码专用，仅开发排查；生产流程应使用 consumeResetCode)
    String getForReset(String phoneNumber);

    /** 原子消费绑定/登录验证码，成功返回 true */
    boolean consumeVerificationCode(String phoneNumber, String code);

    /** 原子消费重置密码验证码，成功返回 true */
    boolean consumeResetCode(String phoneNumber, String code);

    // 短信验证码登录
    User loginBySms(String phoneNumber, String code);

    // 验证码校验通过后绑定/修改手机号
    void verifyAndBind(String phoneNumber, String code, Long userId);
}
