package org.pluchon.forum.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleTagVO {
    private Long id;
    private String name;
    private String colorKey;
}
