package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.user.AuthLoginResultVO;
import org.pluchon.forum.entity.vo.user.UserSessionVO;
import org.pluchon.forum.service.interfaces.user.MailCodeService;
import org.pluchon.forum.service.interfaces.user.UserAuthFlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

// 邮箱验证码登录与绑定
@Tag(name = "邮箱模块", description = "邮箱验证码登录与绑定")
@RestController
@RequestMapping("/mail")
public class MailController {

    @Autowired
    private MailCodeService mailCodeService;

    @Autowired
    private UserAuthFlowService userAuthFlowService;

    @Operation(summary = "邮箱验证码登录", description = "code 为空时发送验证码；code 非空时校验并登录")
    @PostMapping("/login")
    public Result<UserSessionVO> loginByMail(@RequestParam String email,
                                             @RequestParam(required = false) String code,
                                             @RequestParam(required = false) String captchaTicket,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        if (!StringUtils.hasText(code)) {
            return Result.success(userAuthFlowService.sendMailLoginCode(email, captchaTicket));
        }
        AuthLoginResultVO login = userAuthFlowService.loginByMail(email, code, captchaTicket, request);
        applyAuthHeaders(response, login);
        return Result.success(login.getUser());
    }

    @Operation(summary = "绑定/修改邮箱", description = "code 为空时向新邮箱发送验证码；code 非空时校验并绑定到当前账号")
    @PostMapping("/verifyAndBind")
    public Result<String> verifyAndBind(@RequestParam String email, @RequestParam(required = false) String code, HttpServletRequest request) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
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

    private static void applyAuthHeaders(HttpServletResponse response, AuthLoginResultVO login) {
        if (login == null || login.getToken() == null) {
            return;
        }
        response.setHeader(Constant.JWT_NAME, login.getToken());
        response.setHeader(Constant.ACCESS_CONTROL_EXPOSE_HEADERS, Constant.JWT_NAME);
    }
}
