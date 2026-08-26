package org.pluchon.forum.service.interfaces.user;

import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.entity.vo.user.UserLoginLogVO;
import org.pluchon.forum.entity.vo.user.UserLoginRiskHintVO;

import java.util.List;

public interface UserLoginLogService {

    // 记录成功登录
    void recordSuccess(Long userId, String loginType, HttpServletRequest request);

    // 记录失败登录（已知 userId 时）
    void recordFailure(Long userId, String loginType, HttpServletRequest request);

    List<UserLoginLogVO> listRecent(Long userId, int limit);

    // 基于近期登录日志的轻量风险评估
    UserLoginRiskHintVO assessRecentRisk(Long userId);
}
