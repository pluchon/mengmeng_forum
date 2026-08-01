package org.example.forumdemo.service.impl.user;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.user.UserAuthSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class UserAuthSnapshotServiceImpl implements UserAuthSnapshotService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void enrichAuthFields(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (applyFromRedisCache(user)) {
            return;
        }
        applyFromDatabase(user);
    }

    // 用户详情 Hash 命中时直接补全鉴权字段，避免每次请求打 DB
    private boolean applyFromRedisCache(User user) {
        String cacheKey = Constant.REDIS_KEY_USER_INFO + user.getId();
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(cacheKey);
        if (map.isEmpty()
                || !map.containsKey("vipTier")
                || !map.containsKey("state")
                || !map.containsKey("creatorState")) {
            return false;
        }
        user.setVipTier(parseByte(map.get("vipTier"), (byte) 0));
        user.setIsAdmin(parseByte(map.get("isAdmin"), (byte) 0));
        user.setState(parseByte(map.get("state"), (byte) 0));
        user.setCreatorState(parseByte(map.get("creatorState"), (byte) 0));
        String vipExpireMs = map.getOrDefault("vipExpireMs", "").toString();
        if (StringUtils.hasText(vipExpireMs)) {
            user.setVipExpireAt(new Date(Long.parseLong(vipExpireMs.trim())));
        }
        return true;
    }

    private void applyFromDatabase(User user) {
        try {
            User dbUser = userMapper.selectById(user.getId());
            if (dbUser == null) {
                return;
            }
            user.setVipTier(dbUser.getVipTier());
            user.setVipExpireAt(dbUser.getVipExpireAt());
            user.setIsAdmin(dbUser.getIsAdmin());
            user.setState(dbUser.getState());
            user.setCreatorState(dbUser.getCreatorState());
        } catch (Exception e) {
            log.warn("补全用户鉴权字段失败 userId={}, err={}", user.getId(), e.getMessage());
        }
    }

    private static Byte parseByte(Object raw, byte defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Byte.valueOf(raw.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
