package org.example.forumdemo.service.interfaces.mascot;

import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.mascot.MascotChatRequest;

import java.util.Map;

public interface MascotService {

    /**
     * 转发 Python 看板娘接口；普通用户受每日次数限制.
     */
    Map<String, Object> chat(User user, MascotChatRequest request);
}
