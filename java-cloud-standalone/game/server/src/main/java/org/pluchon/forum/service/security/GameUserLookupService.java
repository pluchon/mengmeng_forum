package org.pluchon.forum.service.security;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.cloud.feign.GameUserInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// game 域读取 auth 用户内部视图，禁止构造或依赖 User 实体壳
@Service
public class GameUserLookupService {

    private static final int BATCH_MAX = 50;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    @Lazy
    private GameUserInternalFeignClient gameUserInternalFeignClient;

    public UserInternalVO getById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return gameUserInternalFeignClient.getById(userId);
    }

    public List<UserInternalVO> listByIds(Collection<Long> ids) {
        List<Long> distinct = normalizeIds(ids);
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<UserInternalVO> users = new ArrayList<>(distinct.size());
        for (int i = 0; i < distinct.size(); i += BATCH_MAX) {
            List<Long> chunk = distinct.subList(i, Math.min(i + BATCH_MAX, distinct.size()));
            List<UserInternalVO> batch = gameUserInternalFeignClient.listByIds(chunk);
            if (batch != null) {
                users.addAll(batch);
            }
        }
        return users;
    }

    public Map<Long, UserInternalVO> loadActiveUsers(Collection<Long> ids) {
        Map<Long, UserInternalVO> users = new HashMap<>();
        for (UserInternalVO user : listByIds(ids)) {
            if (user == null || user.getId() == null) {
                continue;
            }
            if (user.getState() != null && user.getState() == STATE_FORBIDDEN) {
                continue;
            }
            users.put(user.getId(), user);
        }
        return users;
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
