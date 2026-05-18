package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.forumdemo.common.captcha.CaptchaTicketPurpose;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.service.interfaces.captcha.CaptchaTicketService;
import org.example.forumdemo.service.interfaces.user.MailCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 邮箱验证码相关接口
 * 设计要点：每个具体业务（登录 / 绑定）的接口同时承担"发码"与"验证"两个阶段
 *   - 不传 code  -> 服务端发送验证码到该邮箱
 *   - 传入 code  -> 服务端校验验证码并执行业务
 * 因此不再单独暴露 /mail/sendCode 这样宽泛的发码接口
 */
@Tag(name = "邮箱模块", description = "邮箱验证码登录与绑定")
@RestController
@RequestMapping("/mail")
public class MailController {

    @Autowired
    private MailCodeService mailCodeService;

    @Autowired
    private CaptchaTicketService captchaTicketService;

    @Operation(summary = "邮箱验证码登录", description = "code 为空时发送验证码；code 非空时校验并登录")
    @PostMapping("/login")
    public Result<User> loginByMail(@RequestParam String email,
                                    @RequestParam(required = false) String code,
                                    @RequestParam(required = false) String captchaTicket,
                                    HttpServletResponse response) {
        if (!StringUtils.hasText(code)) {
            if (!StringUtils.hasText(captchaTicket)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
            }
            if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.MAIL_SEND)) {
                return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
            }
            mailCodeService.send(email);
            return Result.success("验证码已发送至您的邮箱~");
        }
        if (!StringUtils.hasText(captchaTicket)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_REQUIRED);
        }
        if (!captchaTicketService.consume(captchaTicket, CaptchaTicketPurpose.MAIL_LOGIN)) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK);
        }
        User user = mailCodeService.loginByMail(email, code);
        response.setHeader(Constant.JWT_NAME, user.getToken());
        response.setHeader(Constant.ACCESS_CONTROL_EXPOSE_HEADERS, Constant.JWT_NAME);
        return Result.success(user);
    }

    @Operation(summary = "绑定/修改邮箱", description = "code 为空时向新邮箱发送验证码；code 非空时校验并绑定到当前账号")
    @PostMapping("/verifyAndBind")
    public Result<String> verifyAndBind(@RequestParam String email, @RequestParam(required = false) String code, HttpServletRequest request) {
        User sessionUser = (User) request.getAttribute(Constant.USER_SESSION);
        if (sessionUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        if (!StringUtils.hasText(code)) {
            mailCodeService.send(email);
            return Result.success("验证码已发送至您的邮箱~");
        }
        mailCodeService.verifyAndBind(email, code, sessionUser.getId());
        return Result.success("邮箱绑定成功~");
    }
}
