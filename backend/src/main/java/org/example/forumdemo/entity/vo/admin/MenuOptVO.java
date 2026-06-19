package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.List;

// 菜单下拉树节点
@Data
public class MenuOptVO {
    private String id;
    private String title;
    private List<MenuOptVO> children;
}
