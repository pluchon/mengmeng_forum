package org.pluchon.forum.controller;

import org.pluchon.forum.api.content.UserDailyEngagementInternalVO;
import org.pluchon.forum.api.content.UserEngagementInternalApi;
import org.pluchon.forum.service.internal.UserEngagementInternalReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

// 用户当日互动内部接口：契约路径已是 /article/internal/**
@RestController
public class UserEngagementInternalController implements UserEngagementInternalApi {

    @Autowired
    private UserEngagementInternalReadService userEngagementInternalReadService;

    @Override
    public UserDailyEngagementInternalVO getDailyEngagement(Long userId) {
        return userEngagementInternalReadService.getDailyEngagement(userId);
    }
}
