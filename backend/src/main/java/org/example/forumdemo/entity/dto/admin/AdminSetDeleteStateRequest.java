package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

@Data
public class AdminSetDeleteStateRequest {
    private Long id;
    /** 0 否 1 是 */
    private Integer deleteState;
}
