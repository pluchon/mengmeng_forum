package org.pluchon.forum.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PasswordUtils;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 敏感操作前的当前密码校验
// 只依赖 UserMapper，避免 MailCodeService / SMSCodeService 反向依赖 UserService 造成循环依赖
@Component
public class AccountPasswordGuard {

    @Autowired
    private UserMapper userMapper;

    // 读取用户，找不到直接抛异常
    public User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId).ne(User::getDeleteState, 1));
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
    }

    // 校验当前密码，不匹配抛 1211
    public void assertPasswordMatches(User user, String currentPassword) {
        if (!StringUtils.hasText(currentPassword)
                || !PasswordUtils.matches(currentPassword, user.getPassword(), user.getSalt())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CURRENT_PASSWORD_INVALID));
        }
    }

    // 改绑场景：账号已经绑过该类联系方式时，必须先校验当前密码
    // 首次绑定（原本为空）不要求密码，降低新用户完善资料的门槛
    public void assertCanRebind(User user, boolean alreadyBound, String currentPassword) {
        if (!alreadyBound) {
            return;
        }
        assertPasswordMatches(user, currentPassword);
    }
}
