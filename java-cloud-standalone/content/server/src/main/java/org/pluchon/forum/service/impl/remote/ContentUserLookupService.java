package org.pluchon.forum.service.impl.remote;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.api.UserSearchPageInternalVO;
import org.pluchon.forum.cloud.feign.ContentUserInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 内容域使用的认证用户只读查询
@Service
public class ContentUserLookupService {

    private static final int BATCH_MAX = 50;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private ContentUserInternalFeignClient contentUserInternalFeignClient;

    public UserInternalVO queryUserByUserId(Long userId) {
        return getUserInfoById(userId);
    }

    public UserInternalVO getUserInfoById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return contentUserInternalFeignClient.getById(userId);
    }

    public List<UserInternalVO> listByIds(Collection<Long> ids) {
        List<Long> distinct = normalizeIds(ids);
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<UserInternalVO> users = new ArrayList<>(distinct.size());
        for (int i = 0; i < distinct.size(); i += BATCH_MAX) {
            List<Long> chunk = distinct.subList(i, Math.min(i + BATCH_MAX, distinct.size()));
            List<UserInternalVO> batch = contentUserInternalFeignClient.listByIds(chunk);
            if (batch != null) {
                users.addAll(batch);
            }
        }
        return users;
    }

    public Map<Long, UserInternalVO> loadActiveUsers(Collection<Long> ids) {
        List<UserInternalVO> users = listByIds(ids);
        if (users.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserInternalVO> result = new HashMap<>(users.size() * 2);
        for (UserInternalVO user : users) {
            if (user == null || user.getId() == null || isMuted(user)) {
                continue;
            }
            result.put(user.getId(), user);
        }
        return result;
    }

    public Set<Long> filterActiveUserIds(Collection<Long> ids) {
        return loadActiveUsers(ids).keySet();
    }

    public UserSearchPageInternalVO searchByKeyword(String keyword, int pageNum, int pageSize) {
        return contentUserInternalFeignClient.searchByKeyword(keyword, pageNum, pageSize);
    }

    public boolean isMuted(UserInternalVO user) {
        return user != null && user.getState() != null && user.getState() == STATE_FORBIDDEN;
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    }
}
