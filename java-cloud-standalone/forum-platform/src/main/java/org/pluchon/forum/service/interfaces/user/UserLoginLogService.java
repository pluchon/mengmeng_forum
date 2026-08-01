package org.pluchon.forum.service.interfaces.user;

import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.entity.vo.user.UserLoginLogVO;

import java.util.List;

public interface UserLoginLogService {

    void recordSuccess(Long userId, String loginType, HttpServletRequest request);

    List<UserLoginLogVO> listRecent(Long userId, int limit);
}
