package org.example.forumdemo.entity.vo.captcha;

import lombok.Data;

// 行为验证码校验通过后的一次性业务票据
@Data
public class CaptchaCheckResponseVO {

    private String captchaTicket;
}
