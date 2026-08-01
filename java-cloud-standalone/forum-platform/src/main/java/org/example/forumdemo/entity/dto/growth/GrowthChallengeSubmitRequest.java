package org.example.forumdemo.entity.dto.growth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

// 提交成长挑战答案
@Data
public class GrowthChallengeSubmitRequest {
    @NotNull
    private Long attemptId;
    @Valid
    @NotEmpty
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        @NotNull
        private Long questionId;
        private String answer;
    }
}
