package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminRoleRowVO {

    private String id;

    private String createUserString;

    private String createTime;

    private Boolean disabled;

    private String name;

    private String code;

    private Integer sort;

    private String status;

    private Integer type;

    private String description;
}
