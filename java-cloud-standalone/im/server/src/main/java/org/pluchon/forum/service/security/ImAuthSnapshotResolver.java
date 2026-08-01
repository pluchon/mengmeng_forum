package org.pluchon.forum.service.security;

import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.cloud.feign.AuthSnapshotInternalFeignClient;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.common.security.AuthSnapshotResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// im 域通过自身 Feign 客户端取得认证快照，失败时默认拒绝。
@Service
public class ImAuthSnapshotResolver implements AuthSnapshotResolver {

    @Autowired
    private AuthSnapshotInternalFeignClient authSnapshotInternalFeignClient;

    @Override
    public AuthenticatedUser resolve(Long userId, String username) {
        try {
            UserInternalVO source = authSnapshotInternalFeignClient.getById(userId);
            if (source == null || source.getId() == null || source.getState() == null) {
                return null;
            }
            AuthenticatedUser principal = new AuthenticatedUser();
            principal.setId(source.getId());
            principal.setUsername(source.getUsername() == null ? username : source.getUsername());
            principal.setIsAdmin(source.getIsAdmin());
            principal.setCreatorState(source.getCreatorState());
            principal.setState(source.getState());
            return principal;
        } catch (Exception ignored) {
            return null;
        }
    }
}
