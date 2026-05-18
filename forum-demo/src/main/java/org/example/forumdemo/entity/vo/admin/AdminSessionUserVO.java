package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.List;

@Data
public class AdminSessionUserVO {

    private String id;

    private String nickname;

    private String avatar;

    private List<String> roles;

    private List<String> permissions;
}
