package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

@Data
public class AdminLotteryActivityPhaseRequest {

    private Long id;

    private Byte phase;

    /** 可选：对用户开放 0/1 */
    private Byte status;
}
