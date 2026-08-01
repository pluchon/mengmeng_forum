package org.example.forumdemo.entity.dto.driftbottle;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 漂流瓶举报请求
@Data
public class ReportDriftBottleRequest {

    // 举报原因类型
    @NotBlank
    @Length(max = 30)
    private String reasonType;

    // 举报补充说明
    @Length(max = 200)
    private String reasonDetail;
}
