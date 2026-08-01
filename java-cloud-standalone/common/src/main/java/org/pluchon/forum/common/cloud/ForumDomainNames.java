package org.pluchon.forum.common.cloud;

// 与 spring.application.name / forum.domain 对齐的域标识。
// monolith 仅表示「不裁剪 Service」兼容模式，并无独立启动模块。
public final class ForumDomainNames {

    // 兼容开关：Feign remote 条件中表示「本进程装载全部域」
    public static final String MONOLITH = "monolith";
    public static final String AUTH = "auth";
    public static final String CONTENT = "content";
    public static final String IM = "im";
    public static final String GAME = "game";
    public static final String ECONOMY = "economy";
    public static final String AI = "ai";

    private ForumDomainNames() {
    }
}
