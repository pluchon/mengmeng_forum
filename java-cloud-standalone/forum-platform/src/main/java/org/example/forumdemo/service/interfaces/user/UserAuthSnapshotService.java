package org.example.forumdemo.service.interfaces.user;

import org.example.forumdemo.entity.db.User;

// 登录拦截器用：从缓存或 DB 补全 VIP / 管理员 / 账号状态等鉴权字段
public interface UserAuthSnapshotService {

    void enrichAuthFields(User user);
}
