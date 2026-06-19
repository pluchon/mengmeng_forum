package org.example.forumdemo.entity.dto.captcha;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CaptchaCheckRequest {

    @NotBlank
    private String id;

    /** 与 {@link org.example.forumdemo.common.captcha.CaptchaTicketPurpose} 常量一致 */
    @NotBlank
    private String purpose;

    @NotNull
    private ImageCaptchaTrack data;
}
