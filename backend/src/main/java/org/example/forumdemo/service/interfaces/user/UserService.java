package org.example.forumdemo.service.interfaces.user;

import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.user.ModifyUserRequest;
import org.example.forumdemo.entity.dto.user.UserLoginRequest;
import org.example.forumdemo.entity.dto.user.UserResigterRequest;

/**
 * 用户核心账户操作
 * 注意：本接口不持有任何 MultipartFile 参数；图片上传走 FileService，业务侧仅接收 URL
 *      找回密码走 PasswordResetService，避免循环依赖
 */
public interface UserService {

    // ============ 内部共用查询 ============

    User queryUserByUserName(String userName);

    User queryUserByUserId(Long userId);

    // ============ 帖子计数维护（被 ArticleService 反向调用）============

    void addOneById(Long userId);

    void deleteOneById(Long userId);

    // ============ 注册 / 登录 / 信息查询 ============

    void resigter(UserResigterRequest userResigterRequest);

    User login(UserLoginRequest userLoginRequest);

    User getUserInfoById(Long userId);

    // ============ 信息修改 ============

    User modifyUser(ModifyUserRequest modifyUserRequest, Long userId);

    void setMascotModel(Long userId, Long mascotModelId);

    void updatePawssword(Long userId, String oldPassword, String newPassword);

    // 由 FileController 上传图片拿到 URL 后，业务侧 Controller 调用此方法落库
    void updateAvatarUrl(Long userId, String url);

    void updateBackgroundUrl(Long userId, String url);
}
