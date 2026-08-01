package org.pluchon.forum.service.impl.remote;

import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.cloud.feign.ImUserInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// IM 域使用的认证用户只读查询
@Service
public class ImUserLookupService {

    @Autowired
    private ImUserInternalFeignClient imUserInternalFeignClient;

    public UserInternalVO queryUserByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return imUserInternalFeignClient.getById(userId);
    }

    public UserInternalVO getUserInfoById(Long userId) {
        return queryUserByUserId(userId);
    }
}
