package org.pluchon.forum.service.impl.captcha;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.captcha.CaptchaTicketPurpose;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.dto.captcha.CaptchaCheckRequest;
import org.pluchon.forum.entity.vo.captcha.CaptchaCheckResponseVO;
import org.pluchon.forum.service.interfaces.captcha.CaptchaFacadeService;
import org.pluchon.forum.service.interfaces.captcha.CaptchaTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 行为验证码校验与票据签发
@Slf4j
@Service
public class CaptchaFacadeServiceImpl implements CaptchaFacadeService {

    @Autowired
    private ImageCaptchaApplication imageCaptchaApplication;

    @Autowired
    private CaptchaTicketService captchaTicketService;

    @Override
    public CaptchaCheckResponseVO checkAndIssue(CaptchaCheckRequest request) {
        ApiResponse<?> match = imageCaptchaApplication.matching(request.getId(), request.getData());
        if (!match.isSuccess()) {
            // 天爱的失败原因是给开发看的英文短语（轨迹校验失败固定返回 "basic check fail"），
            // 直接透传给用户等于让人看一句看不懂的黑话，只记日志，对外统一用 1168 的文案
            log.info("行为验证码校验未通过 purpose={} reason={}", request.getPurpose(), match.getMsg());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CAPTCHA_CHECK));
        }
        if (!isAllowedPurpose(request.getPurpose())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "purpose 非法"));
        }
        CaptchaCheckResponseVO vo = new CaptchaCheckResponseVO();
        vo.setCaptchaTicket(captchaTicketService.issue(request.getPurpose()));
        return vo;
    }

    private static boolean isAllowedPurpose(String purpose) {
        return CaptchaTicketPurpose.SMS_SEND.equals(purpose)
                || CaptchaTicketPurpose.SMS_LOGIN.equals(purpose)
                || CaptchaTicketPurpose.MAIL_SEND.equals(purpose)
                || CaptchaTicketPurpose.MAIL_LOGIN.equals(purpose)
                || CaptchaTicketPurpose.USER_LOGIN.equals(purpose)
                || CaptchaTicketPurpose.REGISTER.equals(purpose)
                || CaptchaTicketPurpose.RESET_SEND.equals(purpose)
                || CaptchaTicketPurpose.RESET_SUBMIT.equals(purpose);
    }
}
