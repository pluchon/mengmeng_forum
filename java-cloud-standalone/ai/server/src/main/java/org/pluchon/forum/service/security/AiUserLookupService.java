package org.pluchon.forum.service.security;

import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.api.economy.VipTierSnapshotVO;
import org.pluchon.forum.cloud.feign.AiUserInternalFeignClient;
import org.pluchon.forum.cloud.feign.AiVipInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 聚合 AI 所需的 auth 与会员只读字段
@Service
public class AiUserLookupService {

    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    @Lazy
    private AiUserInternalFeignClient aiUserInternalFeignClient;

    @Autowired
    @Lazy
    private AiVipInternalFeignClient aiVipInternalFeignClient;

    public AiUserContext getById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        AiUserContext context = toContext(aiUserInternalFeignClient.getById(userId));
        if (context == null) {
            return null;
        }
        applyVipSnapshot(context);
        return context;
    }

    public Map<Long, AiUserContext> loadActiveUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UserInternalVO> users = aiUserInternalFeignClient.listByIds(ids);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Map<Long, AiUserContext> result = new HashMap<>(users.size() * 2);
        for (UserInternalVO user : users) {
            AiUserContext context = toContext(user);
            if (context == null || context.getState() != null && context.getState() == STATE_FORBIDDEN) {
                continue;
            }
            result.put(context.getId(), context);
        }
        return result;
    }

    private AiUserContext toContext(UserInternalVO user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        AiUserContext context = new AiUserContext();
        context.setId(user.getId());
        context.setUsername(user.getUsername());
        context.setNickname(user.getNickname());
        context.setAvatarUrl(user.getAvatarUrl());
        context.setIsAdmin(user.getIsAdmin());
        context.setCreatorState(user.getCreatorState());
        context.setState(user.getState());
        return context;
    }

    private void applyVipSnapshot(AiUserContext context) {
        VipTierSnapshotVO vip = aiVipInternalFeignClient.tierSnapshot(context.getId());
        if (vip != null) {
            context.setVipTier(vip.getVipTier());
            context.setVipExpireAt(vip.getVipExpireAt());
            context.setVipActive(vip.isVipActive());
        }
    }
}
