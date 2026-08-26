package org.pluchon.forum.common.utils;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

// 认证相关输入正则校验
public class RegexUtil {

    // 邮箱格式正则
    private static final Pattern MAIL_PATTERN = Pattern.compile(
            "^(?i)[a-z0-9]+([._\\-]*[a-z0-9])*@([a-z0-9]+[-a-z0-9]*[a-z0-9]+\\.){1,63}[a-z0-9]+$");

    // 大陆手机号正则
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    // 密码强度正则
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d]{8,20}$");

    // 用户名正则
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[\\u4e00-\\u9fa5a-zA-Z0-9]{4,20}$");

    // 昵称正则
    private static final Pattern NICKNAME_PATTERN = Pattern.compile(
            "^[\\u4e00-\\u9fa5a-zA-Z0-9]{2,20}$");

    // 短信验证码正则
    private static final Pattern SMS_CODE_PATTERN = Pattern.compile("^\\d{4}$");

    // 邮箱验证码正则
    private static final Pattern MAIL_CODE_PATTERN = Pattern.compile("^\\d{6}$");

    // 危险输入与注入特征正则
    private static final Pattern DANGEROUS_INPUT_PATTERN = Pattern.compile(
            "(?i)(<\\s*script|</\\s*script|javascript:|onerror\\s*=|onload\\s*=|"
                    + "union\\s+select|insert\\s+into|drop\\s+table|delete\\s+from|--|/\\*|\\*/|;\\s*--)");

    // 校验邮箱格式
    public static boolean checkMail(String content) {
        if (!StringUtils.hasText(content) || containsDangerousInput(content)) {
            return false;
        }
        return MAIL_PATTERN.matcher(content.trim()).matches();
    }

    // 校验手机号格式
    public static boolean checkMobile(String content) {
        if (!StringUtils.hasText(content) || containsDangerousInput(content)) {
            return false;
        }
        return MOBILE_PATTERN.matcher(content.trim()).matches();
    }

    // 校验密码复杂度
    public static boolean checkPassword(String content) {
        if (!StringUtils.hasText(content) || containsDangerousInput(content)) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(content).matches();
    }

    // 校验用户名格式
    public static boolean checkUserName(String content) {
        if (!StringUtils.hasText(content) || containsDangerousInput(content)) {
            return false;
        }
        return USERNAME_PATTERN.matcher(content.trim()).matches();
    }

    // 校验昵称格式
    public static boolean checkNickname(String content) {
        if (!StringUtils.hasText(content) || containsDangerousInput(content)) {
            return false;
        }
        return NICKNAME_PATTERN.matcher(content.trim()).matches();
    }

    // 校验短信验证码格式
    public static boolean checkSmsCode(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        return SMS_CODE_PATTERN.matcher(content.trim()).matches();
    }

    // 校验邮箱验证码格式
    public static boolean checkMailCode(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        return MAIL_CODE_PATTERN.matcher(content.trim()).matches();
    }

    // 检查是否包含危险注入特征
    public static boolean containsDangerousInput(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        return DANGEROUS_INPUT_PATTERN.matcher(content).find();
    }
}
