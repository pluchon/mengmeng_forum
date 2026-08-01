package org.pluchon.forum.service.security;

import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.cloud.feign.AuthSnapshotInternalFeignClient;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.common.security.AuthSnapshotResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// content 域通过自身 Feign 客户端取得认证快照，失败时默认拒绝。
@Service
public class ContentAuthSnapshotResolver implements AuthSnapshotResolver {

    @Autowired
    private AuthSnapshotInternalFeignClient authSnapshotInternalFeignClient;

    @Override
    public AuthenticatedUser resolve(Long userId, String username) {
        try {
            UserInternalVO source = authSnapshotInternalFeignClient.getById(userId);
            return AuthenticatedUserConverter.from(source, username);
        } catch (Exception ignored) {
            return null;
        }
    }
}
