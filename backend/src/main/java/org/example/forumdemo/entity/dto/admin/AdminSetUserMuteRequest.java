package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

@Data
public class AdminSetUserMuteRequest {
    private Long id;
    /** true=禁言(state=1) false=正常(state=0) */
    private Boolean muted;
}
