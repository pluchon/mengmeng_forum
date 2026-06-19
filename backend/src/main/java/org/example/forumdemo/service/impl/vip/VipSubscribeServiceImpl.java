package org.example.forumdemo.service.impl.vip;

import jakarta.annotation.Resource;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.vip.VipSubscribeDTO;
import org.example.forumdemo.entity.vo.vip.VipSubscribeResultVO;
import org.example.forumdemo.entity.vo.vip.VipStatusVO;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.example.forumdemo.service.interfaces.vip.VipSubscribeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

@Service
public class VipSubscribeServiceImpl implements VipSubscribeService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Resource
    private UserMapper userMapper;

    @Resource
    private PointsService pointsService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private boolean vipActive(User u) {
        Byte tier = u.getVipTier();
        if (tier == null || tier == 0) {
            return false;
        }
        Date exp = u.getVipExpireAt();
        if (exp == null) {
            return true;
        }
        return exp.after(new Date());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipSubscribeResultVO subscribe(Long userId, VipSubscribeDTO dto) {
        Byte tier = dto.getTier();
        if (tier == null || (!Constant.VIP_TIER_PRO.equals(tier) && !Constant.VIP_TIER_MAX.equals(tier))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleteState() != null && user.getDeleteState() == 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }

        Byte cur = user.getVipTier() == null ? Constant.VIP_TIER_FREE : user.getVipTier();
        boolean active = vipActive(user);

        int curOrd = cur == null ? 0 : cur.intValue();
        int wantOrd = tier.intValue();
        if (active && curOrd > wantOrd) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_SUBSCRIBE_TIER));
        }

        int price = Constant.VIP_TIER_PRO.equals(tier) ? Constant.VIP_PRICE_PRO_MONTH : Constant.VIP_PRICE_MAX_MONTH;
        String remark = Constant.VIP_TIER_PRO.equals(tier) ? "订阅 VIP PRO（30 天）" : "订阅 VIP MAX（30 天）";

        Date exp = user.getVipExpireAt();
        Date now = new Date();
        ZonedDateTime base = ZonedDateTime.now(SHANGHAI);
        if (active && exp != null && exp.after(now)) {
            base = exp.toInstant().atZone(SHANGHAI);
        }
        ZonedDateTime newExpireZ = base.plusDays(30);
        Date newExpire = Date.from(newExpireZ.toInstant());

        pointsService.deductPoints(userId, price, Constant.POINTS_SOURCE_VIP_SUBSCRIBE, userId, remark);
        int affected = userMapper.updateVipSubscription(userId, tier, newExpire);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);

        User fresh = userMapper.selectById(userId);
        VipSubscribeResultVO vo = new VipSubscribeResultVO();
        vo.setVipTier(tier);
        vo.setVipExpireAt(newExpire);
        vo.setPointsBalance(fresh != null ? fresh.getPoints() : null);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantTrialVipDays(Long userId, int days) {
        if (days <= 0) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleteState() != null && user.getDeleteState() == 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        Byte tier = Constant.VIP_TIER_PRO;
        Date exp = user.getVipExpireAt();
        Date now = new Date();
        ZonedDateTime base = ZonedDateTime.now(SHANGHAI);
        if (vipActive(user) && exp != null && exp.after(now)) {
            base = exp.toInstant().atZone(SHANGHAI);
        }
        Date newExpire = Date.from(base.plusDays(days).toInstant());
        Byte cur = user.getVipTier() == null ? Constant.VIP_TIER_FREE : user.getVipTier();
        Byte grantTier = tier;
        if (vipActive(user) && cur != null && cur > tier) {
            grantTier = cur;
        }
        int affected = userMapper.updateVipSubscription(userId, grantTier, newExpire);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
    }

    @Override
    public VipStatusVO status(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        VipStatusVO vo = new VipStatusVO();
        vo.setVipTier(user.getVipTier());
        vo.setVipExpireAt(user.getVipExpireAt());
        vo.setPoints(user.getPoints());
        return vo;
    }
}
