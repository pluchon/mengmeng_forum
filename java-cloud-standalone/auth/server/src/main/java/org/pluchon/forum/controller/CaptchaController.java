package org.pluchon.forum.controller;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.HttpRequestUtils;
import org.pluchon.forum.common.utils.RedisWindowCounter;
import org.pluchon.forum.entity.dto.captcha.CaptchaCheckRequest;
import org.pluchon.forum.entity.dto.captcha.CaptchaGenerateRequest;
import org.pluchon.forum.entity.vo.captcha.CaptchaCheckResponseVO;
import org.pluchon.forum.service.interfaces.captcha.CaptchaFacadeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 天爱行为验证码：生成与校验；校验成功后签发一次性 Redis 票据供登录/注册等接口消费
@Tag(name = "行为验证码", description = "登录和注册前的行为验证")
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private ImageCaptchaApplication imageCaptchaApplication;

    @Autowired
    private CaptchaFacadeService captchaFacadeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 生成验证码。支持多类型 type 为天爱常量字符串，如 SLIDER / WORD_IMAGE_CLICK */
    @PostMapping("/generate")
    public ApiResponse<ImageCaptchaVO> generate(@RequestBody(required = false) CaptchaGenerateRequest body,
                                                HttpServletRequest request) {
        // 票据的价值在于"必须先解一次验证码"，出题接口本身不限流的话，
        // 脚本可以无限刷题换票，把这层防护绕成一道形式
        assertGenerateQuota(HttpRequestUtils.resolveClientIp(request));
        String type = (body != null && StringUtils.hasText(body.getType()))
                ? body.getType().trim()
                : CaptchaTypeConstant.SLIDER;
        return imageCaptchaApplication.generateCaptcha(type);
    }

    // 取不到 IP 时放行，避免误伤正常用户
    private void assertGenerateQuota(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        boolean allowed = RedisWindowCounter.tryAcquire(stringRedisTemplate,
                Constant.REDIS_KEY_CAPTCHA_IP_COUNT + clientIp,
                Constant.CAPTCHA_IP_MAX_COUNT,
                Constant.REDIS_TTL_CAPTCHA_IP_COUNT);
        if (!allowed) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CAPTCHA_IP_RATE_LIMIT));
        }
    }

    /** 校验轨迹并签发业务票据 captchaTicket 短 TTL，一次性 */
    @PostMapping("/check")
    public Result<CaptchaCheckResponseVO> check(@Valid @RequestBody CaptchaCheckRequest req) {
        return Result.success(captchaFacadeService.checkAndIssue(req));
    }
}
