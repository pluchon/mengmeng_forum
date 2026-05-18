package org.example.forumdemo.common.captcha;

/**
 * 行为验证码通过后签发的一次性票据用途（须与消费端校验一致）。
 */
public final class CaptchaTicketPurpose {

    public static final String SMS_SEND = "SMS_SEND";
    public static final String SMS_LOGIN = "SMS_LOGIN";
    public static final String MAIL_SEND = "MAIL_SEND";
    public static final String MAIL_LOGIN = "MAIL_LOGIN";
    public static final String USER_LOGIN = "USER_LOGIN";
    public static final String REGISTER = "REGISTER";
    public static final String RESET_SEND = "RESET_SEND";
    public static final String RESET_SUBMIT = "RESET_SUBMIT";

    private CaptchaTicketPurpose() {
    }
}
