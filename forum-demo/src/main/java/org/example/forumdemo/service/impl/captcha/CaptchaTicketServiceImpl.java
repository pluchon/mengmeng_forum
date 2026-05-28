package org.example.forumdemo.service.impl.captcha;

import org.example.forumdemo.service.interfaces.captcha.CaptchaTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * 验证码一次性凭证票据服务实现类
 * 负责在 Redis 中签发（Issue）和消费（Consume）短期有效的业务安全凭证，防止接口被爬虫/脚本绕过校验直接轰炸
 */
@Service
public class CaptchaTicketServiceImpl implements CaptchaTicketService {

    // 1. 定义 Redis 存储的 Key 前缀，用以将验证码凭证与其他缓存数据物理隔离
    private static final String KEY_PREFIX = "forum:captchaTicket:";

    // 2. 注入 Spring 封装的 Redis 字符串操作模板工具
    private final StringRedisTemplate stringRedisTemplate;
    
    // 3. 门票凭证在 Redis 中生存的毫秒时长（过期失效）
    private final long ttlMs;

    /**
     * 4. 构造方法注入所需依赖
     *
     * @param stringRedisTemplate Redis 模板实例
     * @param ttlMs 默认凭证生存时长，若配置文件中没有指定 captcha.expire.default，则默认使用 120000ms（2分钟）
     */
    public CaptchaTicketServiceImpl(StringRedisTemplate stringRedisTemplate, @Value("${captcha.expire.default:120000}") long ttlMs) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ttlMs = ttlMs;
    }

    /**
     * 5. 签发验证码人机检验通过后的“入场门票”凭证
     *
     * @param purpose 本张门票绑定的具体业务意图（如 "REGISTER", "LOGIN", "SEND_SMS"）
     * @return String 随机生成的门票 Token 字符串，只返回给前端一次
     */
    @Override
    public String issue(String purpose) {
        // 6. 生成唯一且无规律的 UUID 并去除横线，防止门票被黑客恶意猜测或穷举
        String ticket = UUID.randomUUID().toString().replace("-", "");
        // 7. 将 [前缀 + 门票号] 作为 Key，[业务意图] 作为 Value 存入 Redis，并设定极短的生存过期时间（例如2分钟）
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + ticket, purpose, Duration.ofMillis(ttlMs));
        // 8. 将门票号返回给前端
        return ticket;
    }

    /**
     * 9. 校验并消费（消耗）门票凭证
     * 遵循“一次性读取即销毁”原则，彻底杜绝重放攻击
     *
     * @param ticket 前端提交的业务请求中携带的门票 Token 字符串
     * @param expectedPurpose 此时业务接口期望的业务意图（如注册接口期望为 "REGISTER"）
     * @return boolean true表示门票真实存在、完全吻合且已被成功消费；false表示非法门票或过期
     */
    @Override
    public boolean consume(String ticket, String expectedPurpose) {
        // 10. 防守校验：如果传入的门票 Token 或者期望的业务意图为空，直接判定不通过
        if (!StringUtils.hasText(ticket) || !StringUtils.hasText(expectedPurpose)) {
            return false;
        }
        // 11. 拼装该张门票在 Redis 中存放的完整 Key
        String key = KEY_PREFIX + ticket;
        // 12. 尝试从 Redis 中获取该门票绑定的业务意图
        String stored = stringRedisTemplate.opsForValue().get(key);
        // 13. 如果 Redis 中查找不到对应的 Value，说明门票非法或已经在 2 分钟内超时失效，直接判定失败
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        // 14. 一旦从 Redis 查出数据，无视校验结果，立即无情删除此门票！
        // 保证此门票在整个物理世界中，仅有且只有一次被检验的机会！
        stringRedisTemplate.delete(key);
        // 15. 核对 Redis 中记录的该门票用途是否与当前业务期望的一致，完全一致则返回 true 允许继续执行
        return expectedPurpose.equals(stored);
    }
}
