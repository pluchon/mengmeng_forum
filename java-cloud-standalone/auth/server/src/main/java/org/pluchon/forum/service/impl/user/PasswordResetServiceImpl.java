package org.pluchon.forum.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PasswordUtils;
import org.pluchon.forum.common.utils.PiiUtils;
import org.pluchon.forum.common.utils.RegexUtil;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.service.interfaces.user.MailCodeService;
import org.pluchon.forum.service.interfaces.user.PasswordResetService;
import org.pluchon.forum.service.interfaces.user.SMSCodeService;
import org.pluchon.forum.common.security.JwtTokenVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MailCodeService mailCodeService;

    @Autowired
    private SMSCodeService smsCodeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtTokenVersionService jwtTokenVersionService;

    @Override
    public void resetByMail(String email, String code, String newPassword) {
        assertParamsValid(email, code, newPassword);
        if (!mailCodeService.consumeResetCode(email, code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_CODE_INVALID));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmailHash, PiiUtils.hmac(email)).ne(User::getDeleteState, 1));
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MAIL_NOT_BOUND));
        }
        updatePassword(user.getId(), newPassword);
    }

    @Override
    public void resetBySms(String phoneNumber, String code, String newPassword) {
        assertParamsValid(phoneNumber, code, newPassword);
        if (!smsCodeService.consumeResetCode(phoneNumber, code)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SMS_CODE_INVALID));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhoneHash, PiiUtils.hmac(phoneNumber)).ne(User::getDeleteState, 1));
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PHONE_NOT_BOUND));
        }
        updatePassword(user.getId(), newPassword);
    }

    @Override
    public void resetByBoundSms(Long userId, String code, String newPassword) {
        String phoneNumber = resolveBoundPhone(userId);
        resetBySms(phoneNumber, code, newPassword);
    }

    // 共用：参数空值 + 密码强度校验
    private void assertParamsValid(String contact, String code, String newPassword) {
        if (!StringUtils.hasText(contact) || !StringUtils.hasText(code) || !StringUtils.hasText(newPassword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!RegexUtil.checkPassword(newPassword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    // 共用：生成新盐 + 新密码哈希落库，并清理用户缓存
    private void updatePassword(Long userId, String newPassword) {
        String newSecret = PasswordUtils.encode(newPassword);
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getSalt, "").set(User::getPassword, newSecret));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MOFIDY_PASSWORD_ERROR));
        }
        jwtTokenVersionService.bump(userId);
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
    }

    private String resolveBoundPhone(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId).ne(User::getDeleteState, 1));
        if (user == null || user.getPhoneNum() == null || user.getPhoneNum().isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PHONE_NOT_BOUND));
        }
        String phoneNumber = PiiUtils.decrypt(user.getPhoneNum());
        if (!RegexUtil.checkMobile(phoneNumber)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return phoneNumber;
    }
}
