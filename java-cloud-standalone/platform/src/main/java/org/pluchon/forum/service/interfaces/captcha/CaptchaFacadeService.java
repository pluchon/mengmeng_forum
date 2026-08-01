package org.pluchon.forum.service.interfaces.captcha;

import org.pluchon.forum.entity.dto.captcha.CaptchaCheckRequest;
import org.pluchon.forum.entity.vo.captcha.CaptchaCheckResponseVO;

// 行为验证码校验与业务票据签发
public interface CaptchaFacadeService {

    CaptchaCheckResponseVO checkAndIssue(CaptchaCheckRequest request);
}
