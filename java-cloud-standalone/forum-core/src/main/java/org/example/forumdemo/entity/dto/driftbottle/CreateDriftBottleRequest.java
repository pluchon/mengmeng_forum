package org.example.forumdemo.entity.dto.driftbottle;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 扔漂流瓶请求
@Data
public class CreateDriftBottleRequest {

    // 瓶子内容
    @NotBlank
    @Length(min = 20, max = 500)
    private String content;

    // 心情标签
    @NotBlank
    private String moodType;
}
