package org.example.forumdemo.entity.vo.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 与 forum-vue-admin 动态路由菜单结构对齐（参见前端 {@code apis/system/menu ListItem}）。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminRouteVO {

    private String id;

    private String parentId;

    private String path;

    private String component;

    private String redirect;

    private Integer type;

    private String title;

    private String icon;

    private Integer sort;

    private Boolean hidden;

    private Boolean keepAlive;

    private Boolean breadcrumb;

    private Boolean affix;

    private Boolean showInTabs;

    private Boolean alwaysShow;

    private String activeMenu;

    private String permission;

    private String status;

    private List<String> roles;

    private List<AdminRouteVO> children;
}
