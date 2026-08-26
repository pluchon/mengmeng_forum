package org.pluchon.forum.service.impl.user;

import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.common.security.AuthSnapshotResolver;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.service.interfaces.user.UserAuthSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// auth 域本地补全请求鉴权主体，避免 common 反向依赖认证实现
@Service
public class AuthLocalSnapshotResolver implements AuthSnapshotResolver {

    @Autowired
    private UserAuthSnapshotService userAuthSnapshotService;

    @Override
    public AuthenticatedUser resolve(Long userId, String username) {
        if (userId == null) {
            return null;
        }
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        userAuthSnapshotService.enrichAuthFields(user);
        if (user.getState() == null) {
            return null;
        }
        AuthenticatedUser principal = new AuthenticatedUser();
        principal.setId(user.getId());
        principal.setUsername(user.getUsername());
        principal.setIsAdmin(user.getIsAdmin());
        principal.setCreatorState(user.getCreatorState());
        principal.setState(user.getState());
        principal.setVipTier(user.getVipTier());
        principal.setVipExpireAt(user.getVipExpireAt());
        return principal;
    }
}
