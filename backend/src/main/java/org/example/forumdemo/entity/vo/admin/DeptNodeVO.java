package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.List;

// 部门树节点
@Data
public class DeptNodeVO {
    private String id;
    private String name;
    private Integer sort;
    private String status;
    private String parentId;
    private String description;
    private String createTime;
    private List<DeptNodeVO> children;
}
