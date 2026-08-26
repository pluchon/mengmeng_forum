package org.pluchon.forum.service.impl.captcha;

import org.pluchon.forum.common.utils.RedisAtomicValueConsumer;
import org.pluchon.forum.service.interfaces.captcha.CaptchaTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

// 验证码一次性凭证票据服务实现类 负责在 Redis 中签发 Issue 和消费 Consume 短期有效的业务安全凭证，防止接口被爬虫/脚本绕过校验直接轰炸
@Service
public class CaptchaTicketServiceImpl implements CaptchaTicketService {

    private static final String KEY_PREFIX = "forum:captchaTicket:";

    private final StringRedisTemplate stringRedisTemplate;

    private final long ttlMs;

    public CaptchaTicketServiceImpl(StringRedisTemplate stringRedisTemplate, @Value("${captcha.expire.default:120000}") long ttlMs) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ttlMs = ttlMs;
    }

    @Override
    public String issue(String purpose) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + ticket, purpose, Duration.ofMillis(ttlMs));
        return ticket;
    }

    @Override
    public boolean consume(String ticket, String expectedPurpose) {
        if (!StringUtils.hasText(ticket) || !StringUtils.hasText(expectedPurpose)) {
            return false;
        }
        String key = KEY_PREFIX + ticket;
        return RedisAtomicValueConsumer.consumeIfMatch(stringRedisTemplate, key, expectedPurpose);
    }
}
