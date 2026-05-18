package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.captcha.CaptchaTicketPurpose;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.service.interfaces.captcha.CaptchaTicketService;
import org.example.forumdemo.service.interfaces.user.SMSCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 短信验证码相关接口
 * 与 MailController 设计一致：每个具体业务接口同时承担发码与验证两阶段
 *   - 不传 code  -> 发送验证码
 *   - 传入 code  -> 校验并执行登录 / 绑定
 */
@Tag(name = "短信模块", description = "短信验证码登录与绑定")
@RestController
@RequestMapping("/sms")
public class SMSController {

    @Autowired
    private SMSCodeService smsCodeService;

    @Autowired
    private CaptchaTicketService captchaTicketService;

    @Operation(summary = "短信验证码登录", description = "code 为空时发送验证码；code 非空时校验并登录")
    @PostMapping("/login")
    public Result<User> loginBySms(@RequestParam String phoneNumber,
                                   @RequestParam(required = false) String code,
                                   @RequestParam(required = false) String captchaTicket,
                                   HttpServletResponse response) {
        if (!StringUtils.hasText(code)) {
            if (!StringUtils.hasText(captchaTicket)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
            }
            if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.SMS_SEND)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
            }
            smsCodeService.send(phoneNumber);
            return Result.success("验证码已发送~");
        }
        if (!StringUtils.hasText(captchaTicket)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
        }
        if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.SMS_LOGIN)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
        }
        User user = smsCodeService.loginBySms(phoneNumber, code);
        response.setHeader(Constant.JWT_NAME, user.getToken());
        response.setHeader(Constant.ACCESS_CONTROL_EXPOSE_HEADERS, Constant.JWT_NAME);
        return Result.success(user);
    }

    @Operation(summary = "绑定/修改手机号", description = "code 为空时向新号码发送验证码；code 非空时校验并绑定")
    @PostMapping("/verifyAndBind")
    public Result<String> verifyAndBind(@RequestParam String phoneNumber, @RequestParam(required = false) String code, HttpServletRequest request) {
        User sessionUser = (User) request.getAttribute(Constant.USER_SESSION);
        if (sessionUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        if (!StringUtils.hasText(code)) {
            smsCodeService.sendForBind(phoneNumber, sessionUser.getId());
            return Result.success("验证码已发送~");
        }
        smsCodeService.verifyAndBind(phoneNumber, code, sessionUser.getId());
        return Result.success("手机号绑定成功~");
    }
}
