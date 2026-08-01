package org.pluchon.forum.entity.dto.driftbottle;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 漂流瓶评论请求
@Data
public class CreateDriftBottleCommentRequest {

    // 评论内容
    @NotBlank
    @Length(max = 200)
    private String content;
}
