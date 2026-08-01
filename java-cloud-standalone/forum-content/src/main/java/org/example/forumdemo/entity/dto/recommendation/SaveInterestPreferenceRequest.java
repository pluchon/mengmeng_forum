package org.example.forumdemo.entity.dto.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

// 保存推荐兴趣设置请求
@Data
public class SaveInterestPreferenceRequest {

    // 是否启用个性化推荐
    @NotNull(message = "个性化开关不能为空")
    private Boolean personalizedEnabled;

    // 用户主动选择的细分板块
    @Size(max = 8, message = "最多选择 8 个细分板块")
    private List<@Positive(message = "板块ID必须为正数") Long> boardIds;
}
