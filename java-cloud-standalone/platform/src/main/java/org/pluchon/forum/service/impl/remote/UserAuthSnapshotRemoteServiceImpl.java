package org.pluchon.forum.service.impl.remote;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.api.economy.VipTierSnapshotVO;
import org.pluchon.forum.cloud.feign.UserInternalFeignClient;
import org.pluchon.forum.cloud.feign.VipInternalFeignClient;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.service.impl.user.UserAuthSnapshotSupport;
import org.pluchon.forum.service.interfaces.user.UserAuthSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

// 非 auth / monolith 域：经 Feign 补全鉴权字段，避免依赖本地 UserMapper
@Slf4j
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'auth'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class UserAuthSnapshotRemoteServiceImpl implements UserAuthSnapshotService {

    @Autowired
    @Lazy
    private UserInternalFeignClient userInternalFeignClient;

    @Autowired
    @Lazy
    private VipInternalFeignClient vipInternalFeignClient;

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
        applyFromRemote(user);
    }

    private void applyFromRemote(User user) {
        try {
            UserInternalVO vo = userInternalFeignClient.getById(user.getId());
            if (vo != null) {
                user.setIsAdmin(vo.getIsAdmin());
                user.setState(vo.getState());
                user.setCreatorState(vo.getCreatorState());
            }
            VipTierSnapshotVO vip = vipInternalFeignClient.tierSnapshot(user.getId());
            if (vip != null) {
                user.setVipTier(vip.getVipTier() != null ? vip.getVipTier() : (byte) 0);
                user.setVipExpireAt(vip.getVipExpireAt());
            }
        } catch (Exception e) {
            log.warn("远程补全用户鉴权字段失败 userId={}, err={}", user.getId(), e.getMessage());
        }
    }
}
