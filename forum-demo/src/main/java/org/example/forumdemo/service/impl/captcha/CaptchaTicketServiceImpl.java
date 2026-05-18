package org.example.forumdemo.service.impl.captcha;

import org.example.forumdemo.service.interfaces.captcha.CaptchaTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class CaptchaTicketServiceImpl implements CaptchaTicketService {

    private static final String KEY_PREFIX = "forum:captchaTicket:";

    private final StringRedisTemplate stringRedisTemplate;
    private final long ttlMs;

    public CaptchaTicketServiceImpl(StringRedisTemplate stringRedisTemplate,
                                    @Value("${captcha.expire.default:120000}") long ttlMs) {
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
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        stringRedisTemplate.delete(key);
        return expectedPurpose.equals(stored);
    }
}
