package org.example.forumdemo.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * @author pluchon
 * @create 2026-03-08-13:34
 * 作者代码水平一般，难免难看，请见谅
 */
//前端发布文章请求
@Data
public class PublishArticleRequest {
    //对应的板块ID
    @NotNull
    private Long boardId;
    @NotNull
    @Length(min = 3,message = "最少输入3个字符")
    private String title;
    @NotNull
    @Length(min = 6,message = "最少输入6个字符内容")
    private String content;
    // 内容类型: 0富文本 1Markdown，默认0
    private Byte contentType = 0;

}
