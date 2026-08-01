package org.example.forumdemo.entity.dto.captcha;

import lombok.Data;

// 行为验证码生成请求参数
@Data
public class CaptchaGenerateRequest {

    // 验证码类型，为空时使用默认滑块验证码
    private String type;
}
