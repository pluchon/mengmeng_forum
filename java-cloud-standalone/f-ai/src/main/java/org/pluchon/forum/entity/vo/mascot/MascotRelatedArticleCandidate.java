package org.pluchon.forum.entity.vo.mascot;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.pluchon.forum.entity.db.Article;

// 相关帖子召回候选
@Data
@AllArgsConstructor
public class MascotRelatedArticleCandidate {

    private Article article;
    private double relevanceScore;
}
