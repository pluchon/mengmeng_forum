package org.pluchon.forum.service.impl.article.auditguard;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.RedisWindowCounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// 单篇帖子的每日提交审核次数上限
//
// AuditRetryLimitGuard 挡的是 audit_retry_count，但编辑一篇已发布的帖子会把它归零，
// 于是「提交 → 被拒 3 次 → 随便改一下 → 又有 3 次」可以无限循环，
// 而每一轮都是一次完整的 AI 审核（文本 + 逐张图片 + 视频），是创作链路里最贵的调用。
//
// 这里按帖子做一个不会被编辑重置的每日计数：内容真改了仍然可以重试，
// 但磨审核磨不下去。用 Redis 而不是加字段，是为了避开一次数据库迁移。
@Component
public class AuditDailySubmitGuard implements ArticleAuditSubmitGuard {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public int order() {
        // 排在重试次数之后：先给用户看更具体的「次数已用完」，再看每日上限
        return 60;
    }

    @Override
    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        Long articleId = context.getArticleId();
        if (articleId == null || stringRedisTemplate == null) {
            return ArticleAuditSubmitGuardResult.pass();
        }
        boolean allowed = RedisWindowCounter.tryAcquire(stringRedisTemplate,
                Constant.REDIS_KEY_ARTICLE_AUDIT_DAILY + articleId,
                Constant.ARTICLE_AUDIT_DAILY_MAX,
                Constant.REDIS_TTL_ARTICLE_AUDIT_DAILY);
        if (!allowed) {
            return ArticleAuditSubmitGuardResult.fail(Result.fail(ResultCode.FAILED_RATE_LIMITED,
                    "这篇帖子今天提交审核的次数已达上限，明天再试或联系管理员"));
        }
        return ArticleAuditSubmitGuardResult.pass();
    }
}
