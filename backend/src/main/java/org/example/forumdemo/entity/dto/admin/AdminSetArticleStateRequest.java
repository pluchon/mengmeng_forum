package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

@Data
public class AdminSetArticleStateRequest {
    private Long id;
    /** 审核状态: 0 正常 1 禁用 */
    private Integer state;
}
