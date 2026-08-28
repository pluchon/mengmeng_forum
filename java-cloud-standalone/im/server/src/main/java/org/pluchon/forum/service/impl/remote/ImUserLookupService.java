package org.pluchon.forum.service.impl.remote;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.cloud.feign.ImUserInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<UserInternalVO> queryUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UserInternalVO> users = imUserInternalFeignClient.listByIds(userIds);
        return users == null ? List.of() : users;
    }
}
