package org.pluchon.forum.controller;

import org.pluchon.forum.api.ai.MascotPreferenceInternalApi;
import org.pluchon.forum.service.interfaces.mascot.MascotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 看板娘偏好内部接口 供 auth 等跨服务调用
@RestController
public class MascotInternalController implements MascotPreferenceInternalApi {

    @Autowired
    private MascotService mascotService;

    @Override
    public void setMascotModel(
            @PathVariable Long userId,
            @RequestParam("mascotModelId") Long mascotModelId) {
        mascotService.setUserMascotPreference(userId, mascotModelId);
    }
}
