package org.example.forumdemo.service.impl.admin;

import jakarta.annotation.Resource;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.admin.AdminForumMemberPreviewVO;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.admin.AdminForumMemberService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;

@Service
public class AdminForumMemberServiceImpl implements AdminForumMemberService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private UserMapper userMapper;

    @Override
    public AdminForumMemberPreviewVO previewMember(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User u = userMapper.selectById(userId);
        if (u == null || (u.getDeleteState() != null && u.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        AdminForumMemberPreviewVO vo = new AdminForumMemberPreviewVO();
        vo.setId(String.valueOf(u.getId()));
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setGender(u.getGender() != null ? u.getGender().intValue() : 2);
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setArticleCount(u.getArticleCount());
        vo.setPoints(u.getPoints());
        vo.setVipTier(u.getVipTier() != null ? u.getVipTier().intValue() : 0);
        vo.setVipExpireAt(u.getVipExpireAt() != null ? DF.format(u.getVipExpireAt()) : null);
        vo.setState(u.getState() != null ? u.getState().intValue() : 0);
        vo.setIsAdmin(u.getIsAdmin() != null ? u.getIsAdmin().intValue() : 0);
        vo.setCreateTime(u.getCreateTime() != null ? DF.format(u.getCreateTime()) : "");
        return vo;
    }
}
