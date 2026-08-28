package org.pluchon.forum.common.security;

import io.jsonwebtoken.Claims;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.JWTUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

// 单个令牌的吊销名单
//
// 令牌版本号只能一次性作废某账号的全部令牌，用来处理改密码、封禁这类"全端下线"。
// 而"退出登录"只该退当前设备，所以按令牌的 jti 单独吊销。
// 记录 TTL 取令牌剩余有效期：令牌自然过期后本来就用不了，名单没必要再留着。
@Service
public class JwtRevocationService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 吊销一个令牌；已过期或缺少 jti 的旧令牌无需处理
    public void revoke(Claims claims) {
        String tokenId = JWTUtils.readTokenId(claims);
        long ttlSeconds = JWTUtils.remainingSeconds(claims);
        if (!StringUtils.hasText(tokenId) || ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(Constant.REDIS_KEY_JWT_REVOKED + tokenId, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    // 本次改动之前签发的令牌没有 jti，一律视为未吊销，避免存量用户被误登出
    public boolean isRevoked(Claims claims) {
        String tokenId = JWTUtils.readTokenId(claims);
        if (!StringUtils.hasText(tokenId)) {
            return false;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(Constant.REDIS_KEY_JWT_REVOKED + tokenId));
    }
}
