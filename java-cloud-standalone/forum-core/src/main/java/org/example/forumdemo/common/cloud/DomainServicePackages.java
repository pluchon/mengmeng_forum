package org.example.forumdemo.common.cloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// 按 forum.domain 决定哪些 service.impl 子包保留；跨域由 Feign remote 适配器补齐
public final class DomainServicePackages {

    // 始终保留的实现包（基础设施 / 跨进程可安全本地执行）
    private static final Set<String> SHARED_IMPL_PACKAGES = Set.of(
            "org.example.forumdemo.service.impl.common",
            "org.example.forumdemo.service.impl.websocket",
            "org.example.forumdemo.service.impl.remote"
    );

    // 跨域共享的具体实现类（不全量放开所在包，避免拉入无关依赖）
    private static final Set<String> SHARED_CLASS_SIMPLE_NAMES = Set.of(
            "UserAuthSnapshotServiceImpl",
            "AuthTokenService",
            "JwtTokenVersionService",
            "UserDerivedCacheInvalidator",
            "UserFollowServiceImpl",
            "AiHubServiceImpl",
            "FileServiceImpl",
            "ArticleHotRankingServiceImpl",
            "HotArticleRedisOps",
            "VipCenterServiceImpl"
    );

    private static final Map<String, Set<String>> DOMAIN_IMPL_PACKAGES = new HashMap<>();

    static {
        DOMAIN_IMPL_PACKAGES.put(ForumDomainNames.AUTH, Set.of(
                "org.example.forumdemo.service.impl.user",
                "org.example.forumdemo.service.impl.captcha"
        ));
        DOMAIN_IMPL_PACKAGES.put(ForumDomainNames.CONTENT, Set.of(
                "org.example.forumdemo.service.impl.article",
                "org.example.forumdemo.service.impl.board",
                "org.example.forumdemo.service.impl.favorite",
                "org.example.forumdemo.service.impl.file",
                "org.example.forumdemo.service.impl.recommendation",
                "org.example.forumdemo.service.impl.search"
        ));
        DOMAIN_IMPL_PACKAGES.put(ForumDomainNames.IM, Set.of(
                "org.example.forumdemo.service.impl.message",
                "org.example.forumdemo.service.impl.groupchat",
                "org.example.forumdemo.service.impl.voice"
        ));
        DOMAIN_IMPL_PACKAGES.put(ForumDomainNames.GAME, Set.of(
                "org.example.forumdemo.service.impl.game"
        ));
        DOMAIN_IMPL_PACKAGES.put(ForumDomainNames.ECONOMY, Set.of(
                "org.example.forumdemo.service.impl.points",
                "org.example.forumdemo.service.impl.checkin",
                "org.example.forumdemo.service.impl.vip",
                "org.example.forumdemo.service.impl.lottery",
                "org.example.forumdemo.service.impl.shop",
                "org.example.forumdemo.service.impl.growth"
        ));
        DOMAIN_IMPL_PACKAGES.put(ForumDomainNames.AI, Set.of(
                "org.example.forumdemo.service.impl.ai",
                "org.example.forumdemo.service.impl.mascot",
                "org.example.forumdemo.service.impl.driftbottle"
        ));
    }

    private DomainServicePackages() {
    }

    public static Set<String> allowedImplPackages(String domain) {
        if (domain == null || domain.isBlank()) {
            return Collections.emptySet();
        }
        String key = domain.trim().toLowerCase(Locale.ROOT);
        if (ForumDomainNames.MONOLITH.equals(key)) {
            return Collections.emptySet();
        }
        Set<String> allowed = new HashSet<>(SHARED_IMPL_PACKAGES);
        Set<String> owned = DOMAIN_IMPL_PACKAGES.getOrDefault(key, Collections.emptySet());
        allowed.addAll(owned);
        return allowed;
    }

    public static boolean isSharedClass(String simpleClassName) {
        return simpleClassName != null && SHARED_CLASS_SIMPLE_NAMES.contains(simpleClassName);
    }

    public static boolean isServiceImplClass(String className) {
        return className != null && className.startsWith("org.example.forumdemo.service.impl.");
    }
}
