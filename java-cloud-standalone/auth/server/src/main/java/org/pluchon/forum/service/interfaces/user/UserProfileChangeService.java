package org.pluchon.forum.service.interfaces.user;

import org.pluchon.forum.entity.dto.user.ProfileChangeRequest;
import org.pluchon.forum.entity.vo.user.ProfileChangeStatusVO;

public interface UserProfileChangeService {

    ProfileChangeStatusVO submit(Long userId, ProfileChangeRequest request);

    ProfileChangeStatusVO latest(Long userId, String fieldType);

    void retryPendingRequests();
}
