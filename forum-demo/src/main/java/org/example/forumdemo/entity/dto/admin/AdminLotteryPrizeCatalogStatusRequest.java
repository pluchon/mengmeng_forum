package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

@Data
public class AdminLotteryPrizeCatalogStatusRequest {

    private Long id;

    /** 0草稿 1上架 2下架 */
    private Integer catalogStatus;
}
