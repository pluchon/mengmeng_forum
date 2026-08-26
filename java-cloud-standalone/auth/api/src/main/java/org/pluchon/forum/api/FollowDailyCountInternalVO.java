package org.pluchon.forum.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 每日新增粉丝内部统计
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowDailyCountInternalVO {

    private String statDate;

    private Long count;
}
