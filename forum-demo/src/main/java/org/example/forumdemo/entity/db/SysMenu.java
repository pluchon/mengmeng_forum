package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId
    private String id;

    private String parentId;

    private String path;

    private String component;

    private String redirect;

    private Integer type;

    private String title;

    private String icon;

    private Integer sort;

    private Integer hidden;

    private Integer keepAlive;

    private Integer breadcrumb;

    private Integer affix;

    private Integer showInTabs;

    private Integer alwaysShow;

    private String activeMenu;

    private String permission;

    private String status;
}
