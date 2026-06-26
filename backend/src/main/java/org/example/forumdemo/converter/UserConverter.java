package org.example.forumdemo.converter;

import org.example.forumdemo.common.utils.PiiUtils;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.user.AuthLoginResultVO;
import org.example.forumdemo.entity.vo.user.UserSessionVO;

// 用户实体与 VO 转换
public final class UserConverter {

    private UserConverter() {
    }

    public static UserSessionVO toSessionVO(User user) {
        if (user == null) {
            return null;
        }
        UserSessionVO vo = new UserSessionVO();
        vo.setId(user.getId());
        vo.setToken(user.getToken());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhoneNum(PiiUtils.maskPhone(user.getPhoneNum()));
        vo.setEmail(PiiUtils.decrypt(user.getEmail()));
        vo.setGender(user.getGender());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setBackgroundUrl(user.getBackgroundUrl());
        vo.setArticleCount(user.getArticleCount());
        vo.setIsAdmin(user.getIsAdmin());
        vo.setPoints(user.getPoints());
        vo.setLotteryPityDraws(user.getLotteryPityDraws());
        vo.setLotterySurpriseClaimed(user.getLotterySurpriseClaimed());
        vo.setVipTier(user.getVipTier());
        vo.setVipExpireAt(user.getVipExpireAt());
        vo.setMascotModelId(user.getMascotModelId());
        vo.setRemark(user.getRemark());
        vo.setIpRegion(user.getIpRegion());
        vo.setState(user.getState());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    public static AuthLoginResultVO toAuthLoginResult(User user) {
        if (user == null) {
            return null;
        }
        AuthLoginResultVO vo = new AuthLoginResultVO();
        vo.setToken(user.getToken());
        UserSessionVO session = toSessionVO(user);
        session.setToken(user.getToken());
        vo.setUser(session);
        return vo;
    }
}
