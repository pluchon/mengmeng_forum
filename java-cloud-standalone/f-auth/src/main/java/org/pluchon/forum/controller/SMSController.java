package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.user.AuthLoginResultVO;
import org.pluchon.forum.entity.vo.user.UserSessionVO;
import org.pluchon.forum.service.interfaces.user.SMSCodeService;
import org.pluchon.forum.service.interfaces.user.UserAuthFlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

// 短信验证码登录与绑定
@Tag(name = "短信模块", description = "短信验证码登录与绑定")
@RestController
@RequestMapping("/sms")
public class SMSController {

    @Autowired
    private SMSCodeService smsCodeService;

    @Autowired
    private UserAuthFlowService userAuthFlowService;

    @Operation(summary = "短信验证码登录", description = "code 为空时发送验证码；code 非空时校验并登录")
    @PostMapping("/login")
    public Result<UserSessionVO> loginBySms(@RequestParam String phoneNumber,
                                            @RequestParam(required = false) String code,
                                            @RequestParam(required = false) String captchaTicket,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        if (!StringUtils.hasText(code)) {
            return Result.success(userAuthFlowService.sendSmsLoginCode(phoneNumber, captchaTicket));
        }
        AuthLoginResultVO login = userAuthFlowService.loginBySms(phoneNumber, code, captchaTicket, request);
        applyAuthHeaders(response, login);
        return Result.success(login.getUser());
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

    private static void applyAuthHeaders(HttpServletResponse response, AuthLoginResultVO login) {
        if (login == null || login.getToken() == null) {
            return;
        }
        response.setHeader(Constant.JWT_NAME, login.getToken());
        response.setHeader(Constant.ACCESS_CONTROL_EXPOSE_HEADERS, Constant.JWT_NAME);
    }
}
