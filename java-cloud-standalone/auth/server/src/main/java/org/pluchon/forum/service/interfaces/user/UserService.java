package org.pluchon.forum.service.interfaces.user;

import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.user.ModifyUserRequest;
import org.pluchon.forum.entity.dto.user.UserLoginRequest;
import org.pluchon.forum.entity.dto.user.UserResigterRequest;
import org.pluchon.forum.entity.vo.user.UserSecurityAssessmentVO;

import jakarta.servlet.http.HttpServletRequest;

// 用户核心账户操作 注意：本接口不持有任何 MultipartFile 参数；图片上传走 FileService，业务侧仅接收 URL 找回密码走 PasswordResetService，避免循环依赖
public interface UserService {

    

    User queryUserByUserName(String userName);

    User queryUserByUserId(Long userId);

    

    void addOneById(Long userId);

    void deleteOneById(Long userId);

    

    void resigter(UserResigterRequest userResigterRequest);

    User login(UserLoginRequest userLoginRequest, HttpServletRequest httpRequest);

    void logout(Long userId);

    User getUserInfoById(Long userId);

    UserSecurityAssessmentVO assessSecurity(Long userId);

    

    User modifyUser(ModifyUserRequest modifyUserRequest, Long userId);

    void applyReviewedProfileChange(Long userId, String fieldType, String content);

    void setMascotModel(Long userId, Long mascotModelId);

    void updatePawssword(Long userId, String oldPassword, String newPassword);

    // 由 FileController 上传图片拿到 URL 后，业务侧 Controller 调用此方法落库
    void updateAvatarUrl(Long userId, String url);

    void updateBackgroundUrl(Long userId, String url);
}
