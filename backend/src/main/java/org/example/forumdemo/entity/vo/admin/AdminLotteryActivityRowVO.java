package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminLotteryActivityRowVO {

    private Long id;

    private String title;

    private String coverImageUrl;

    private Long publisherId;

    private Integer costPointsPerDraw;

    private Byte status;

    private Byte phase;

    private Byte deleteState;

    private String createTime;

    private String updateTime;
}
