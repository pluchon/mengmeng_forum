package org.pluchon.forum.entity.vo.mascot;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.pluchon.forum.api.content.ArticleInternalVO;

// 相关帖子召回候选
@Data
@AllArgsConstructor
public class MascotRelatedArticleCandidate {

    private ArticleInternalVO article;
    private double relevanceScore;
}
