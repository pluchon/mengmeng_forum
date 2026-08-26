package org.pluchon.forum.common.security;

// 由各可启动服务在本地实现；common 不依赖任何领域 API 或 Feign 客户端
public interface AuthSnapshotResolver {

    AuthenticatedUser resolve(Long userId, String username);
}
