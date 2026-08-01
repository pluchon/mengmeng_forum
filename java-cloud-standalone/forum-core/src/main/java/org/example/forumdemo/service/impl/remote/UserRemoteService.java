package org.example.forumdemo.service.impl.remote;

import org.example.forumdemo.cloud.feign.UserInternalFeignClient;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.converter.UserInternalConverter;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.user.ModifyUserRequest;
import org.example.forumdemo.entity.dto.user.UserLoginRequest;
import org.example.forumdemo.entity.dto.user.UserResigterRequest;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// 非 auth 域通过 Feign 访问用户查询与发帖计数（契约为 UserInternalVO）
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'auth'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class UserRemoteService implements UserService {

    @Autowired
    private UserInternalFeignClient userInternalFeignClient;

    @Override
    public User queryUserByUserName(String userName) {
        return UserInternalConverter.toUserShell(userInternalFeignClient.getByUsername(userName));
    }

    @Override
    public User queryUserByUserId(Long userId) {
        return UserInternalConverter.toUserShell(userInternalFeignClient.getById(userId));
    }

    @Override
    public void addOneById(Long userId) {
        userInternalFeignClient.incrementArticleCount(userId);
    }

    @Override
    public void deleteOneById(Long userId) {
        userInternalFeignClient.decrementArticleCount(userId);
    }

    @Override
    public void resigter(UserResigterRequest userResigterRequest) {
        throw unsupported("register");
    }

    @Override
    public User login(UserLoginRequest userLoginRequest) {
        throw unsupported("login");
    }

    @Override
    public void logout(Long userId) {
        throw unsupported("logout");
    }

    @Override
    public User getUserInfoById(Long userId) {
        return UserInternalConverter.toUserShell(userInternalFeignClient.getById(userId));
    }

    @Override
    public User modifyUser(ModifyUserRequest modifyUserRequest, Long userId) {
        throw unsupported("modifyUser");
    }

    @Override
    public void setMascotModel(Long userId, Long mascotModelId) {
        throw unsupported("setMascotModel");
    }

    @Override
    public void updatePawssword(Long userId, String oldPassword, String newPassword) {
        throw unsupported("updatePassword");
    }

    @Override
    public void updateAvatarUrl(Long userId, String url) {
        throw unsupported("updateAvatarUrl");
    }

    @Override
    public void updateBackgroundUrl(Long userId, String url) {
        throw unsupported("updateBackgroundUrl");
    }

    private ApplicationException unsupported(String action) {
        return new ApplicationException(Result.fail(
                ResultCode.ERROR_SERVICES,
                "用户写操作请走 auth 服务: " + action
        ));
    }
}
