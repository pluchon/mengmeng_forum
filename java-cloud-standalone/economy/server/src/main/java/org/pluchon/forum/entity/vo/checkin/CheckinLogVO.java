package org.pluchon.forum.entity.vo.checkin;

import lombok.Data;

import java.util.Date;

// 用户签到记录响应
@Data
public class CheckinLogVO {

    private Long id;

    private Date createTime;

    private Date attributionDate;

    private String checkinType;

    private Integer points;

    private Integer streakDays;

    private String surpriseLabel;
}
