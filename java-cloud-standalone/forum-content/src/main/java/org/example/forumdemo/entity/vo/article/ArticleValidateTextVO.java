package org.example.forumdemo.entity.vo.article;

import lombok.Data;

// 帖子正文安全校验结果
@Data
public class ArticleValidateTextVO {

    private Boolean isAllowed;
    private String reason;
}
