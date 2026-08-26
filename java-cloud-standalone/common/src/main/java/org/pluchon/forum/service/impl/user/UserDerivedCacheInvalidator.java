package org.pluchon.forum.service.impl.user;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// 用户资料、积分、VIP 变更后失效相关派生缓存
@Component
public class UserDerivedCacheInvalidator {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void invalidateUserCaches(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        TransactionHooks.afterCommit(() -> {
            stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
            stringRedisTemplate.delete(Constant.REDIS_KEY_CHECKIN_STATUS + userId);
        });
    }

    public void invalidateUserCachesNow(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
        stringRedisTemplate.delete(Constant.REDIS_KEY_CHECKIN_STATUS + userId);
    }
}
