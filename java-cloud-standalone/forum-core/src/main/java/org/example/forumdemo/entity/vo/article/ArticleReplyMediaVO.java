package org.example.forumdemo.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleReplyMediaVO {
    private Byte mediaType;
    private String mediaUrl;
    private Long shopId;
}
