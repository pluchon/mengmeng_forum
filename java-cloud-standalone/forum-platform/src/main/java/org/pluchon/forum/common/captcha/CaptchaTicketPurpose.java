package org.pluchon.forum.common.captcha;

// 验证码票据，存入缓存，前端拿着这个票据才可以进行验证码的调用~
// 尽可能减少人机刷验证码接口~
public final class CaptchaTicketPurpose {

    public static final String SMS_SEND = "SMS_SEND";
    public static final String SMS_LOGIN = "SMS_LOGIN";
    public static final String MAIL_SEND = "MAIL_SEND";
    public static final String MAIL_LOGIN = "MAIL_LOGIN";
    public static final String USER_LOGIN = "USER_LOGIN";
    public static final String REGISTER = "REGISTER";
    public static final String RESET_SEND = "RESET_SEND";
    public static final String RESET_SUBMIT = "RESET_SUBMIT";

    private CaptchaTicketPurpose() {}
}
