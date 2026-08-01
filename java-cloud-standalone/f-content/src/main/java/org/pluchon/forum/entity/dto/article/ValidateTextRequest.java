package org.pluchon.forum.entity.dto.article;

import lombok.Data;

// 文章内容安全审核请求
@Data
public class ValidateTextRequest {
    private String content;
    private String text;
}
