package org.pluchon.forum.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.CaptchaUtils;
import org.pluchon.forum.common.utils.RegexUtil;
import org.pluchon.forum.common.MailUtil;
import org.pluchon.forum.common.utils.RedisAtomicValueConsumer;
import org.pluchon.forum.common.utils.RedisWindowCounter;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.utils.PiiUtils;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.service.interfaces.user.MailCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

// 邮箱验证服务实现
@Service
public class MailCodeServiceImpl implements MailCodeService {

    private static final Long TIMEOUT = 300L;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private AccountPasswordGuard accountPasswordGuard;

    @Override
    public void sendForLogin(String email, String clientIp) {
        // 未注册的邮箱不发码，把"该邮箱还没有绑定账号"提前到第一步告诉用户
        assertMailRegistered(email);
        sendInternal(email, Constant.REDIS_KEY_MAIL_VERIFY, clientIp);
    }

    @Override
    public void sendForBind(String email, Long userId, String clientIp) {
        assertCanBindMail(email, userId);
        sendInternal(email, Constant.REDIS_KEY_MAIL_VERIFY, clientIp);
    }

    @Override
    public void sendForReset(String email, String clientIp) {
        assertMailRegistered(email);
        sendInternal(email, Constant.REDIS_KEY_MAIL_VERIFY_RESET, clientIp);
    }

    private void sendInternal(String email, String prefix, String clientIp) {
        if (!RegexUtil.checkMail(email)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 邮箱维度的限制换个地址就绕开了，IP 维度合计计数才挡得住批量发信
        assertIpQuota(clientIp);
        // 记录一个冷标记，防止用户频繁盗刷接口，时间为60S
        String cooldownKey = Constant.REDIS_KEY_MAIL_COOLDOWN + email;
        if (stringRedisTemplate.hasKey(cooldownKey)) {
            // 证明60S内缓存中存在了这个冷标记，也就是说已经发送过验证码了
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_RATE_LIMIT));
        }
        // 在内存中记录发送次数
        String countKey = Constant.REDIS_KEY_MAIL_COUNT + email;
        Long count = stringRedisTemplate.opsForValue().increment(countKey);
        // 首次发送，给一个30分钟内的次数限制
        if (count != null && count == 1) {
            stringRedisTemplate.expire(countKey, Constant.REDIS_TTL_MAIL_COUNT, TimeUnit.SECONDS);
        }
        // 已经达到了最大的发送数量
        if (count != null && count > Constant.MAIL_MAX_COUNT) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_RATE_LIMIT));
        }
        // 获取6位数端验证码
        String code = CaptchaUtils.getCapthca(6);
        stringRedisTemplate.opsForValue().set(prefix + email, code, TIMEOUT, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", 60L, TimeUnit.SECONDS);
        String subject = "[萌萌论坛]身份验证码";
        String content = "您的验证码是：" + code + "，5分钟内有效。若非本人操作请忽略。";
        Boolean sendSuccess = mailUtil.sendSampleMail(email, subject, content);
        if (!sendSuccess) {
            // 如果发送不成功，要把缓存中的刚刚设置的验证码的信息删了
            stringRedisTemplate.delete(prefix + email);
            stringRedisTemplate.delete(cooldownKey);
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
    }

    // 与短信共用同一个 IP 计数键，短信 + 邮件合计
    private void assertIpQuota(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        boolean allowed = RedisWindowCounter.tryAcquire(stringRedisTemplate,
                Constant.REDIS_KEY_CODE_IP_COUNT + clientIp,
                Constant.CODE_SEND_IP_MAX_COUNT,
                Constant.REDIS_TTL_CODE_IP_COUNT);
        if (!allowed) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CODE_IP_RATE_LIMIT));
        }
    }

    @Override
    public User loginByMail(String email, String code) {
        if (!consumeVerificationCode(email, code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_CODE_INVALID));
        }
        User user = findByMail(email);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_NOT_BOUND));
        }
        user.setToken(authTokenService.issueLoginToken(user));
        user.setEmail(PiiUtils.decrypt(user.getEmail()));
        user.setPhoneNum(PiiUtils.maskPhone(user.getPhoneNum()));
        return user;
    }

    @Override
    public void verifyAndBind(String email, String code, Long userId, String currentPassword) {
        // 改绑等于把账号找回入口迁到新邮箱上，必须先证明是本人
        User current = accountPasswordGuard.requireUser(userId);
        boolean alreadyBound = StringUtils.hasText(current.getEmail());
        accountPasswordGuard.assertCanRebind(current, alreadyBound, currentPassword);
        if (!consumeVerificationCode(email, code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_CODE_INVALID));
        }
        String emailHash = PiiUtils.hmac(email);
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmailHash, emailHash).ne(User::getDeleteState, 1));
        if (existing != null && !existing.getId().equals(userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_ALREADY_BOUND));
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getEmail, PiiUtils.encrypt(email)).set(User::getEmailHash, emailHash));
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
    }

    @Override
    public boolean consumeVerificationCode(String email, String code) {
        if (!RegexUtil.checkMail(email) || !RegexUtil.checkMailCode(code)) {
            return false;
        }
        return RedisAtomicValueConsumer.consumeIfMatch(
                stringRedisTemplate, Constant.REDIS_KEY_MAIL_VERIFY + email, code);
    }

    @Override
    public boolean consumeResetCode(String email, String code) {
        if (!RegexUtil.checkMail(email) || !RegexUtil.checkMailCode(code)) {
            return false;
        }
        return RedisAtomicValueConsumer.consumeIfMatch(
                stringRedisTemplate, Constant.REDIS_KEY_MAIL_VERIFY_RESET + email, code);
    }

    private void assertMailRegistered(String email) {
        if (!RegexUtil.checkMail(email)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (findByMail(email) == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_NOT_BOUND));
        }
    }

    // 改绑前就把"已被他人占用"告诉用户，不让人白等一封信
    private void assertCanBindMail(String email, Long userId) {
        if (!RegexUtil.checkMail(email)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User existing = findByMail(email);
        if (existing == null) {
            return;
        }
        if (existing.getId().equals(userId)) {
            throw new ApplicationException(Result.fail("该邮箱已经是当前绑定邮箱，无需重复绑定"));
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_ALREADY_BOUND));
    }

    private User findByMail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmailHash, PiiUtils.hmac(email)).ne(User::getDeleteState, 1));
    }
}
