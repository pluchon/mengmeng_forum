package org.example.forumdemo.entity.vo.mascot;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.example.forumdemo.entity.db.Article;

// 相关帖子召回候选
@Data
@AllArgsConstructor
public class MascotRelatedArticleCandidate {

    private Article article;
    private double relevanceScore;
}
