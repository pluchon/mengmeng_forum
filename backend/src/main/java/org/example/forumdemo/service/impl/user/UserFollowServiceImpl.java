package org.example.forumdemo.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.db.UserFollow;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.entity.vo.user.UserFollowListItemVO;
import org.example.forumdemo.entity.vo.user.UserFollowStatsVO;
import org.example.forumdemo.mapper.UserFollowMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.user.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserFollowServiceImpl implements UserFollowService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long followerId, Long followeeId) {
        validateFollowPair(followerId, followeeId);
        UserFollow existing = userFollowMapper.selectOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        if (existing != null) {
            return;
        }
        UserFollow row = new UserFollow();
        row.setFollowerId(followerId);
        row.setFolloweeId(followeeId);
        try {
            userFollowMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            return;
        }
        log.info("用户 {} 关注了 {}", followerId, followeeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long followerId, Long followeeId) {
        validateFollowPair(followerId, followeeId);
        int deleted = userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        if (deleted <= 0) {
            return;
        }
        log.info("用户 {} 取消关注 {}", followerId, followeeId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null || followerId <= 0 || followeeId <= 0) {
            return false;
        }
        if (followerId.equals(followeeId)) {
            return false;
        }
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        return count != null && count > 0;
    }

    @Override
    public UserFollowStatsVO getStats(Long userId, Long viewerId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User user = userMapper.selectById(userId);
        if (user == null || (user.getDeleteState() != null && user.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        Long followingCount = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId));
        Long followerCount = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, userId));
        Boolean isFollowing = null;
        if (viewerId != null && viewerId > 0 && !viewerId.equals(userId)) {
            isFollowing = isFollowing(viewerId, userId);
        }
        return new UserFollowStatsVO(userId, followingCount, followerCount, isFollowing);
    }

    @Override
    public Map<Long, UserFollowStatsVO> getBatchStats(Collection<Long> userIds, Long viewerId) {
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

        Map<Long, Long> followingCounts = new HashMap<>();
        List<UserFollow> outgoing = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .in(UserFollow::getFollowerId, ids)
                .select(UserFollow::getFollowerId));
        for (UserFollow row : outgoing) {
            followingCounts.merge(row.getFollowerId(), 1L, Long::sum);
        }

        Map<Long, Long> followerCounts = new HashMap<>();
        List<UserFollow> incoming = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .in(UserFollow::getFolloweeId, ids)
                .select(UserFollow::getFolloweeId));
        for (UserFollow row : incoming) {
            followerCounts.merge(row.getFolloweeId(), 1L, Long::sum);
        }

        Set<Long> viewerFollowing = viewerId != null && viewerId > 0
                ? batchFollowingTargets(viewerId, ids)
                : Set.of();
        Map<Long, UserFollowStatsVO> result = new HashMap<>();
        for (Long userId : ids) {
            result.put(userId, new UserFollowStatsVO(
                    userId,
                    followingCounts.getOrDefault(userId, 0L),
                    followerCounts.getOrDefault(userId, 0L),
                    viewerFollowing.contains(userId)
            ));
        }
        return result;
    }

    @Override
    public Set<Long> listFollowingIds(Long followerId) {
        if (followerId == null || followerId <= 0) {
            return Set.of();
        }
        List<UserFollow> rows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .select(UserFollow::getFolloweeId));
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(UserFollow::getFolloweeId).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public PageResult<UserFollowListItemVO> listFollowingPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize) {
        validateProfileUser(profileUserId);
        int p = PageUtils.getValidPageNum(pageNum);
        int s = normalizeListPageSize(pageSize);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, profileUserId)
                .orderByDesc(UserFollow::getCreateTime);
        applyKeywordFilter(wrapper, keyword, profileUserId, true);
        Page<UserFollow> page = userFollowMapper.selectPage(PageUtils.getPage(p, s), wrapper);
        List<UserFollowListItemVO> records = buildListItems(page.getRecords(), profileUserId, viewerId, false);
        return new PageResult<>(records, page.getTotal(), p, s, page.getPages(), page.hasNext());
    }

    @Override
    public PageResult<UserFollowListItemVO> listFollowersPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize) {
        validateProfileUser(profileUserId);
        int p = PageUtils.getValidPageNum(pageNum);
        int s = normalizeListPageSize(pageSize);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, profileUserId)
                .orderByDesc(UserFollow::getCreateTime);
        applyKeywordFilter(wrapper, keyword, profileUserId, false);
        Page<UserFollow> page = userFollowMapper.selectPage(PageUtils.getPage(p, s), wrapper);
        List<UserFollowListItemVO> records = buildListItems(page.getRecords(), profileUserId, viewerId, true);
        return new PageResult<>(records, page.getTotal(), p, s, page.getPages(), page.hasNext());
    }

    private int normalizeListPageSize(Integer pageSize) {
        int s = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return Math.min(s, 50);
    }

    private void validateProfileUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User user = userMapper.selectById(userId);
        if (user == null || (user.getDeleteState() != null && user.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }

    /** 在关注/粉丝范围内按昵称、用户名或简介模糊匹配（不走 AI / 分词扩展） */
    private void applyKeywordFilter(LambdaQueryWrapper<UserFollow> wrapper, String keyword,
            Long profileUserId, boolean followingList) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        List<Long> scopedIds = resolveScopedUserIds(profileUserId, followingList);
        if (scopedIds.isEmpty()) {
            wrapper.eq(UserFollow::getId, -1L);
            return;
        }
        List<Long> matched = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, scopedIds)
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN)
                .and(q -> q.like(User::getNickname, kw)
                        .or().like(User::getUsername, kw)
                        .or().like(User::getRemark, kw))
                .select(User::getId))
                .stream()
                .map(User::getId)
                .toList();
        if (matched.isEmpty()) {
            wrapper.eq(UserFollow::getId, -1L);
            return;
        }
        if (followingList) {
            wrapper.in(UserFollow::getFolloweeId, matched);
        } else {
            wrapper.in(UserFollow::getFollowerId, matched);
        }
    }

    private List<Long> resolveScopedUserIds(Long profileUserId, boolean followingList) {
        List<UserFollow> rows = userFollowMapper.selectList(
                followingList
                        ? new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, profileUserId)
                        : new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFolloweeId, profileUserId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(r -> followingList ? r.getFolloweeId() : r.getFollowerId())
                .distinct()
                .toList();
    }

    private List<UserFollowListItemVO> buildListItems(List<UserFollow> follows, Long profileUserId,
            Long viewerId, boolean followersList) {
        if (follows == null || follows.isEmpty()) {
            return List.of();
        }
        List<Long> targetIds = follows.stream()
                .map(f -> followersList ? f.getFollowerId() : f.getFolloweeId())
                .toList();
        Map<Long, User> userMap = loadActiveUsers(targetIds);
        Set<Long> viewerFollowing = (viewerId != null && viewerId > 0)
                ? batchFollowingTargets(viewerId, targetIds)
                : Set.of();
        List<UserFollowListItemVO> out = new ArrayList<>(follows.size());
        for (UserFollow row : follows) {
            Long uid = followersList ? row.getFollowerId() : row.getFolloweeId();
            User user = userMap.get(uid);
            if (user == null) {
                continue;
            }
            UserFollowListItemVO item = new UserFollowListItemVO();
            item.setUser(new UserBriefVO(user));
            item.setFollowTime(row.getCreateTime());
            item.setIsFollowing(viewerFollowing.contains(uid));
            item.setFollowsProfileUser(followersList);
            out.add(item);
        }
        return out;
    }

    private Map<Long, User> loadActiveUsers(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, ids)
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN));
        Map<Long, User> map = new HashMap<>();
        if (users != null) {
            for (User u : users) {
                map.put(u.getId(), u);
            }
        }
        return map;
    }

    private Set<Long> batchFollowingTargets(Long viewerId, Collection<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return Set.of();
        }
        List<UserFollow> rows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, viewerId)
                .in(UserFollow::getFolloweeId, targetUserIds)
                .select(UserFollow::getFolloweeId));
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(UserFollow::getFolloweeId).collect(Collectors.toCollection(HashSet::new));
    }

    private void validateFollowPair(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null || followerId <= 0 || followeeId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (followerId.equals(followeeId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CANNOT_FOLLOW_SELF));
        }
        User followee = userMapper.selectById(followeeId);
        if (followee == null || (followee.getDeleteState() != null && followee.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }
}
