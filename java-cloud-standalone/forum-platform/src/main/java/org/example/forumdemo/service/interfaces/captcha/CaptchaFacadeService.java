package org.example.forumdemo.service.interfaces.captcha;

import org.example.forumdemo.entity.dto.captcha.CaptchaCheckRequest;
import org.example.forumdemo.entity.vo.captcha.CaptchaCheckResponseVO;

// 行为验证码校验与业务票据签发
public interface CaptchaFacadeService {

    CaptchaCheckResponseVO checkAndIssue(CaptchaCheckRequest request);
}
