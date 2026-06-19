package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

import java.util.List;

// 管理端批量删除用户请求
@Data
public class AdminDeleteUsersRequest {
    private List<String> ids;
}
