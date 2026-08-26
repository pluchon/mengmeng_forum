package org.pluchon.forum.service.security;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.common.security.AuthenticatedUser;

// 将 auth api 的非敏感内部视图转换为本服务请求主体
final class AuthenticatedUserConverter {

    private AuthenticatedUserConverter() {
    }

    static AuthenticatedUser from(UserInternalVO source, String tokenUsername) {
        if (source == null || source.getId() == null || source.getState() == null) {
            return null;
        }
        AuthenticatedUser principal = new AuthenticatedUser();
        principal.setId(source.getId());
        principal.setUsername(source.getUsername() == null ? tokenUsername : source.getUsername());
        principal.setIsAdmin(source.getIsAdmin());
        principal.setCreatorState(source.getCreatorState());
        principal.setState(source.getState());
        return principal;
    }
}
