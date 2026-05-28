package org.example.forumdemo.controller.user;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.example.forumdemo.common.captcha.CaptchaTicketPurpose;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.captcha.CaptchaCheckRequest;
import org.example.forumdemo.service.interfaces.captcha.CaptchaTicketService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 天爱行为验证码：生成与校验；校验成功后签发一次性 Redis 票据供登录/注册等接口消费。
 */
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private final ImageCaptchaApplication imageCaptchaApplication;
    private final CaptchaTicketService captchaTicketService;

    public CaptchaController(ImageCaptchaApplication imageCaptchaApplication,
                             CaptchaTicketService captchaTicketService) {
        this.imageCaptchaApplication = imageCaptchaApplication;
        this.captchaTicketService = captchaTicketService;
    }

    /**
     * 生成验证码。支持多类型（type 为天爱常量字符串，如 SLIDER / WORD_IMAGE_CLICK）。
     */
    @PostMapping("/generate")
    public ApiResponse<ImageCaptchaVO> generate(@RequestBody(required = false) Map<String, String> body) {
        String type = (body != null && StringUtils.hasText(body.get("type")))
                ? body.get("type").trim()
                : CaptchaTypeConstant.SLIDER;
        return imageCaptchaApplication.generateCaptcha(type);
    }

    /**
     * 校验轨迹并签发业务票据 {@code captchaTicket}（短 TTL，一次性）。
     */
    @PostMapping("/check")
    public Result<Map<String, String>> check(@Valid @RequestBody CaptchaCheckRequest req) {
        ApiResponse<?> match = imageCaptchaApplication.matching(req.getId(), req.getData());
        if (!match.isSuccess()) {
            return Result.fail(ResultCode.FAILED_CAPTCHA_CHECK, match.getMsg());
        }
        if (!isAllowedPurpose(req.getPurpose())) {
            return Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "purpose 非法");
        }
        String ticket = captchaTicketService.issue(req.getPurpose());
        return Result.successData(Collections.singletonMap("captchaTicket", ticket));
    }

    private static boolean isAllowedPurpose(String p) {
        return CaptchaTicketPurpose.SMS_SEND.equals(p)
                || CaptchaTicketPurpose.SMS_LOGIN.equals(p)
                || CaptchaTicketPurpose.MAIL_SEND.equals(p)
                || CaptchaTicketPurpose.MAIL_LOGIN.equals(p)
                || CaptchaTicketPurpose.USER_LOGIN.equals(p)
                || CaptchaTicketPurpose.REGISTER.equals(p)
                || CaptchaTicketPurpose.RESET_SEND.equals(p)
                || CaptchaTicketPurpose.RESET_SUBMIT.equals(p);
    }
}
