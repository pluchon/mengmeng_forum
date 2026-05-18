package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class AdminLotteryActivityDetailVO {

    private Long id;

    private String title;

    private String description;

    private String coverImageUrl;

    private Long publisherId;

    private Integer costPointsPerDraw;

    private Byte status;

    private Byte phase;

    private Byte deleteState;

    private Date startTime;

    private Date endTime;

    private String createTime;

    private String updateTime;

    private List<AdminLotteryPrizeLineVO> prizeLines = new ArrayList<>();
}
