package org.example.forumdemo.entity.dto.article;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-03-09-13:13
 * 作者代码水平一般，难免难看，请见谅
 */
//前端更新帖子信息的请求
@Data
public class UpdateArticleRequest {
    private Long articleId;
    private String title;
    private String content;

    // TODO: 加一个字段，表示我们可以更改该帖子的类型，比如是markdown还是富文本
    // private Byte contentType;
}
