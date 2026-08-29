package org.pluchon.forum.service.internal;

import org.pluchon.forum.common.constant.ForumTimeZone;
import org.pluchon.forum.api.content.UserDailyEngagementInternalVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

// 用户当日评论/点赞计数，供 economy 抽奖任务裁决
@Service
public class UserEngagementInternalReadService {

    private static final ZoneId ZONE_SH = ForumTimeZone.ZONE_ID;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public UserDailyEngagementInternalVO getDailyEngagement(Long userId) {
        UserDailyEngagementInternalVO vo = new UserDailyEngagementInternalVO();
        vo.setUserId(userId);
        if (userId == null || userId <= 0) {
            vo.setCommentCount(0);
            vo.setLikeCount(0);
            return vo;
        }
        LocalDate today = LocalDate.now(ZONE_SH);
        Timestamp start = Timestamp.valueOf(LocalDateTime.of(today, LocalTime.MIN));
        Timestamp end = Timestamp.valueOf(LocalDateTime.of(today.plusDays(1), LocalTime.MIN));

        Integer commentCount = jdbcTemplate.queryForObject(
                "SELECT ("
                        + " (SELECT COUNT(*) FROM article_reply "
                        + "  WHERE post_user_id = ? AND delete_state = 0 AND create_time >= ? AND create_time < ?)"
                        + " + "
                        + " (SELECT COUNT(*) FROM article_sub_reply "
                        + "  WHERE post_user_id = ? AND delete_state = 0 AND create_time >= ? AND create_time < ?)"
                        + ") AS cnt",
                Integer.class,
                userId, start, end, userId, start, end);

        Integer likeCount = jdbcTemplate.queryForObject(
                "SELECT ("
                        + " (SELECT COUNT(*) FROM article_like "
                        + "  WHERE user_id = ? AND create_time >= ? AND create_time < ?)"
                        + " + "
                        + " (SELECT COUNT(*) FROM article_reply_like "
                        + "  WHERE user_id = ? AND create_time >= ? AND create_time < ?)"
                        + " + "
                        + " (SELECT COUNT(*) FROM article_sub_reply_like "
                        + "  WHERE user_id = ? AND create_time >= ? AND create_time < ?)"
                        + ") AS cnt",
                Integer.class,
                userId, start, end, userId, start, end, userId, start, end);

        vo.setCommentCount(commentCount == null ? 0 : commentCount);
        vo.setLikeCount(likeCount == null ? 0 : likeCount);
        return vo;
    }
}
