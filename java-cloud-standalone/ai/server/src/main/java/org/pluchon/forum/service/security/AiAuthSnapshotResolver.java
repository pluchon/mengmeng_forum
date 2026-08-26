package org.pluchon.forum.service.security;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.cloud.feign.AuthSnapshotInternalFeignClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.common.security.AuthSnapshotResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// ai 域认证快照：无用户→null(401)；auth 不可用→503
@Slf4j
@Service
public class AiAuthSnapshotResolver implements AuthSnapshotResolver {

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
        } catch (ApplicationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("auth 快照不可用 userId={}: {}", userId, ex.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
        }
    }
}
