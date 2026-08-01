package org.pluchon.forum.service.impl.user;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.service.interfaces.user.UserAuthSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

// auth / monolith / 未配置 domain：本地 UserMapper 补全鉴权字段
@Slf4j
@Service
@ConditionalOnExpression("'auth'.equals('${forum.domain:}') || 'monolith'.equals('${forum.domain:}') || ''.equals('${forum.domain:}')")
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
        if (UserAuthSnapshotSupport.applyFromRedisCache(user, stringRedisTemplate)) {
            return;
        }
        applyFromDatabase(user);
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
}
