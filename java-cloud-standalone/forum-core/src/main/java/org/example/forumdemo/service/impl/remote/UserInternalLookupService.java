package org.example.forumdemo.service.impl.remote;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.forum.api.auth.UserInternalVO;
import org.example.forum.api.auth.UserSearchPageInternalVO;
import org.example.forum.cloud.feign.UserInternalFeignClient;
import org.example.forumdemo.common.cloud.ForumDomainNames;
import org.example.forumdemo.converter.UserInternalConverter;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 跨域用户批量/单条查询：auth/monolith 走本地 Mapper，其余走 Feign
@Service
public class UserInternalLookupService {

    private static final int BATCH_MAX = 50;
    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired(required = false)
    @Lazy
    private UserInternalFeignClient userInternalFeignClient;

    @Autowired(required = false)
    private UserMapper userMapper;

    @Value("${forum.domain:monolith}")
    private String forumDomain;

    public User getById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        if (useFeign()) {
            return UserInternalConverter.toUserShell(userInternalFeignClient.getById(userId));
        }
        if (userMapper == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }

    public List<User> listByIds(Collection<Long> ids) {
        List<Long> distinct = normalizeIds(ids);
        if (distinct.isEmpty()) {
            return List.of();
        }
        if (useFeign()) {
            List<UserInternalVO> vos = new ArrayList<>(distinct.size());
            for (int i = 0; i < distinct.size(); i += BATCH_MAX) {
                List<Long> chunk = distinct.subList(i, Math.min(i + BATCH_MAX, distinct.size()));
                List<UserInternalVO> batch = userInternalFeignClient.listByIds(chunk);
                if (batch != null) {
                    vos.addAll(batch);
                }
            }
            return vos.stream()
                    .map(UserInternalConverter::toUserShell)
                    .filter(Objects::nonNull)
                    .toList();
        }
        if (userMapper == null) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, distinct));
    }

    public Map<Long, User> loadActiveUsers(Collection<Long> ids) {
        List<User> users = listByIds(ids);
        if (users.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new HashMap<>(users.size() * 2);
        for (User user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            if (user.getDeleteState() != null && user.getDeleteState() == DELETE_TRUE) {
                continue;
            }
            if (user.getState() != null && user.getState() == STATE_FORBIDDEN) {
                continue;
            }
            map.put(user.getId(), user);
        }
        return map;
    }

    public Set<Long> filterActiveUserIds(Collection<Long> ids) {
        return loadActiveUsers(ids).keySet();
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

    private boolean useFeign() {
        if (forumDomain == null || forumDomain.isBlank()) {
            return false;
        }
        String domain = forumDomain.trim().toLowerCase();
        return !ForumDomainNames.MONOLITH.equals(domain)
                && !ForumDomainNames.AUTH.equals(domain)
                && userInternalFeignClient != null;
    }

    public boolean usesRemoteLookup() {
        return useFeign();
    }

    /** 用户名/昵称字面搜索；远程走 auth Feign，本地走 UserMapper */
    public UserSearchPageInternalVO searchByKeyword(String keyword, int pageNum, int pageSize) {
        if (useFeign()) {
            return userInternalFeignClient.searchByKeyword(keyword, pageNum, pageSize);
        }
        throw new UnsupportedOperationException("本地用户搜索请走 SearchServiceImpl 的 UserMapper 路径");
    }
}
