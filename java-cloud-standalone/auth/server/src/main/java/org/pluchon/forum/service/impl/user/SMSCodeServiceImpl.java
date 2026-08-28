package org.pluchon.forum.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.CaptchaUtils;
import org.pluchon.forum.common.utils.RegexUtil;
import org.pluchon.forum.common.SMSUtils;
import org.pluchon.forum.common.utils.RedisAtomicValueConsumer;
import org.pluchon.forum.common.utils.RedisWindowCounter;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.utils.PiiUtils;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.service.interfaces.user.SMSCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

// 短信验证服务实现
@Service
public class SMSCodeServiceImpl implements SMSCodeService {

    private static final Long TIMEOUT = 60L;

    @Autowired
    private SMSUtils smsUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private AccountPasswordGuard accountPasswordGuard;

    @Override
    public void sendForLogin(String phoneNumber, String clientIp) {
        // 短信真实计费，未注册的号码不发码，避免被当成短信轰炸网关
        assertPhoneRegistered(phoneNumber);
        sendInternal(phoneNumber, Constant.REDIS_KEY_SMS_VERIFY, clientIp);
    }

    @Override
    public void sendForBind(String phoneNumber, Long userId, String clientIp) {
        assertCanBindPhone(phoneNumber, userId);
        sendInternal(phoneNumber, Constant.REDIS_KEY_SMS_VERIFY, clientIp);
    }

    @Override
    public void sendForReset(String phoneNumber, String clientIp) {
        assertPhoneRegistered(phoneNumber);
        sendInternal(phoneNumber, Constant.REDIS_KEY_SMS_VERIFY_RESET, clientIp);
    }

    @Override
    public void sendForResetBound(Long userId, String clientIp) {
        String phoneNumber = resolveBoundPhone(userId);
        sendInternal(phoneNumber, Constant.REDIS_KEY_SMS_VERIFY_RESET, clientIp);
    }

    private void sendInternal(String phoneNumber, String prefix, String clientIp) {
        if (!RegexUtil.checkMobile(phoneNumber)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 手机号维度的冷却与次数限制只能挡住盯着同一个号码打的脚本，
        // 换号轰炸要靠 IP 维度的合计计数来兜底
        assertIpQuota(clientIp);
        String cooldownKey = Constant.REDIS_KEY_SMS_COOLDOWN + phoneNumber;
        if (stringRedisTemplate.hasKey(cooldownKey)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SMS_RATE_LIMIT));
        }
        String countKey = Constant.REDIS_KEY_SMS_COUNT + phoneNumber;
        Long count = stringRedisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(countKey, Constant.REDIS_TTL_SMS_COUNT, TimeUnit.SECONDS);
        }
        if (count != null && count > Constant.SMS_MAX_COUNT) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SMS_RATE_LIMIT));
        }
        String code = CaptchaUtils.getCapthca(4);
        stringRedisTemplate.opsForValue().set(prefix + phoneNumber, code, TIMEOUT, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", TIMEOUT, TimeUnit.SECONDS);
        try {
            smsUtils.sendMessage("100001", phoneNumber, "{\"code\":\"" + code + "\", \"min\":\"1\"}");
        } catch (Exception e) {
            stringRedisTemplate.delete(prefix + phoneNumber);
            stringRedisTemplate.delete(cooldownKey);
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
    }

    // 同一 IP 的短信 + 邮件发送合计计数，取不到 IP 时放行，不误伤正常用户
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
    public User loginBySms(String phoneNumber, String code) {
        if (!consumeVerificationCode(phoneNumber, code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SMS_CODE_INVALID));
        }
        User user = findByPhone(phoneNumber);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PHONE_NOT_BOUND));
        }
        user.setToken(authTokenService.issueLoginToken(user));
        user.setEmail(PiiUtils.decrypt(user.getEmail()));
        user.setPhoneNum(PiiUtils.maskPhone(user.getPhoneNum()));
        return user;
    }

    @Override
    public void verifyAndBind(String phoneNumber, String code, Long userId, String currentPassword) {
        // 改绑等于把账号找回入口迁到新号码上，必须先证明是本人
        User current = accountPasswordGuard.requireUser(userId);
        boolean alreadyBound = StringUtils.hasText(current.getPhoneNum());
        accountPasswordGuard.assertCanRebind(current, alreadyBound, currentPassword);
        if (!consumeVerificationCode(phoneNumber, code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SMS_CODE_INVALID));
        }
        String phoneHash = PiiUtils.hmac(phoneNumber);
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhoneHash, phoneHash).ne(User::getDeleteState, 1));
        if (existing != null && !existing.getId().equals(userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PHONE_ALREADY_BOUND));
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getPhoneNum, PiiUtils.encrypt(phoneNumber)).set(User::getPhoneHash, phoneHash));
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
    }

    @Override
    public boolean consumeVerificationCode(String phoneNumber, String code) {
        if (!RegexUtil.checkMobile(phoneNumber) || !RegexUtil.checkSmsCode(code)) {
            return false;
        }
        return RedisAtomicValueConsumer.consumeIfMatch(
                stringRedisTemplate, Constant.REDIS_KEY_SMS_VERIFY + phoneNumber, code);
    }

    @Override
    public boolean consumeResetCode(String phoneNumber, String code) {
        if (!RegexUtil.checkMobile(phoneNumber) || !RegexUtil.checkSmsCode(code)) {
            return false;
        }
        return RedisAtomicValueConsumer.consumeIfMatch(
                stringRedisTemplate, Constant.REDIS_KEY_SMS_VERIFY_RESET + phoneNumber, code);
    }

    // 发码前确认号码确实绑定了账号，把"该手机号还没有绑定账号"提前到第一步告诉用户
    private void assertPhoneRegistered(String phoneNumber) {
        if (!RegexUtil.checkMobile(phoneNumber)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (findByPhone(phoneNumber) == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PHONE_NOT_BOUND));
        }
    }

    private User findByPhone(String phoneNumber) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhoneHash, PiiUtils.hmac(phoneNumber)).ne(User::getDeleteState, 1));
    }

    private void assertCanBindPhone(String phoneNumber, Long userId) {
        if (!RegexUtil.checkMobile(phoneNumber)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User existing = findByPhone(phoneNumber);
        if (existing == null) {
            return;
        }
        if (existing.getId().equals(userId)) {
            throw new ApplicationException(Result.fail("该手机号已经是当前绑定手机号，无需重复绑定"));
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED_PHONE_ALREADY_BOUND));
    }

    private String resolveBoundPhone(Long userId) {
        User user = accountPasswordGuard.requireUser(userId);
        if (user.getPhoneNum() == null || user.getPhoneNum().isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PHONE_NOT_BOUND));
        }
        String phoneNumber = PiiUtils.decrypt(user.getPhoneNum());
        if (!RegexUtil.checkMobile(phoneNumber)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return phoneNumber;
    }
}
