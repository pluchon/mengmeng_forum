package org.pluchon.forum.common.utils;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.constant.ForumBusinessConstants;
import org.pluchon.forum.entity.db.Article;

import java.time.Duration;
import java.time.Instant;

// 热帖综合分纯计算（无 Mapper / Redis），供 content 与 AI 共用
public final class ArticleHotScoreUtils {

    private ArticleHotScoreUtils() {
    }

    public static double computeHotScore(Article article) {
        if (article == null || article.getCreateTime() == null) {
            return 0;
        }
        long ageHours = Duration.between(article.getCreateTime().toInstant(), Instant.now()).toHours();
        if (ageHours > ForumBusinessConstants.HOT_RANK_WINDOW_DAYS * 24L) {
            return 0;
        }
        int like = article.getLikeCount() == null ? 0 : article.getLikeCount();
        int visit = article.getVisitCount() == null ? 0 : article.getVisitCount();
        int favorite = article.getFavoriteCount() == null ? 0 : article.getFavoriteCount();
        int reply = article.getReplyCount() == null ? 0 : article.getReplyCount();
        int sub = article.getSubReplyCount() == null ? 0 : article.getSubReplyCount();
        double base = like * Constant.HOT_SCORE_WEIGHT_LIKE
                + visit * Constant.HOT_SCORE_WEIGHT_VISIT
                + favorite * Constant.HOT_SCORE_WEIGHT_FAVORITE
                + (reply + sub) * Constant.HOT_SCORE_WEIGHT_REPLY;
        double decay = 1.0 / (1.0 + ageHours / 24.0);
        double boost = ageHours <= ForumBusinessConstants.HOT_RANK_NEW_POST_HOURS
                ? ForumBusinessConstants.HOT_RANK_NEW_POST_BOOST
                : 1.0;
        return base * decay * boost;
    }
}
