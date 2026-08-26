package org.pluchon.forum.entity.dto.captcha;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.pluchon.forum.common.captcha.CaptchaTicketPurpose;

@Data
public class CaptchaCheckRequest {

    @NotBlank
    private String id;

    // 与 {@link CaptchaTicketPurpose} 常量一致
    @NotBlank
    private String purpose;

    @NotNull
    private ImageCaptchaTrack data;
}
