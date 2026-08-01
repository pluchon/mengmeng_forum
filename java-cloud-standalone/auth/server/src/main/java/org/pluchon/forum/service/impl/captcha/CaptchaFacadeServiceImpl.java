package org.pluchon.forum.service.impl.captcha;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.response.ApiResponse;
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
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CAPTCHA_CHECK, match.getMsg()));
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
