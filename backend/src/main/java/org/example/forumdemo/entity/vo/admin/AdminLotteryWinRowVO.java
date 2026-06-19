package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminLotteryWinRowVO {

    private Long id;

    private Long userId;

    private String nickname;

    private String prizeName;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer grantPoints;

    private Byte isJackpot;

    private String createTime;
}
