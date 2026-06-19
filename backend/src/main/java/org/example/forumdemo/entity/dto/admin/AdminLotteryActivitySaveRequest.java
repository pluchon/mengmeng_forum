package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class AdminLotteryActivitySaveRequest {

    private Long id;

    private String title;

    private String description;

    private String coverImageUrl;

    private Integer costPointsPerDraw;

    private Byte status;

    private Byte phase;

    private Date startTime;

    private Date endTime;

    private List<AdminLotteryPrizeLineSaveDTO> lines;
}
