package org.example.forumdemo.service.impl.article;

import org.example.forumdemo.common.enums.HotArticleTrendDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotArticleTrendPolicyTest {

    @Test
    void periodScoreShouldOnlyUseDailyVisitLikeAndFavoriteDeltas() {
        double score = ArticleHotRankingServiceImpl.computePeriodScore(10, 3, 2);

        assertEquals(14.4D, score, 0.0001D);
    }

    @Test
    void scoreComparisonShouldReturnDirectionalState() {
        assertEquals(HotArticleTrendDirection.UP,
                ArticleHotRankingServiceImpl.comparePeriodScores(12D, 8D));
        assertEquals(HotArticleTrendDirection.DOWN,
                ArticleHotRankingServiceImpl.comparePeriodScores(5D, 8D));
        assertEquals(HotArticleTrendDirection.STABLE,
                ArticleHotRankingServiceImpl.comparePeriodScores(8D, 8D));
    }
}
