package org.example.forumdemo.service.interfaces.checkin;

import org.example.forumdemo.entity.db.CheckinLog;
import org.example.forumdemo.entity.vo.checkin.CheckinResultResponse;
import org.example.forumdemo.entity.vo.checkin.CheckinRuleMonthResponse;
import org.example.forumdemo.entity.vo.checkin.CheckinStatusResponse;
import org.example.forumdemo.entity.vo.common.CursorPageResult;
import org.example.forumdemo.entity.vo.common.PageResult;

//签到模块
public interface CheckinService {

    /**
     * 执行签到, 一次返回本次得分 + 签后最新状态
     */
    CheckinResultResponse doCheckin(Long userId);

    /**
     * 查询当前签到状态快照, 用于进入签到页时展示日历高亮 / 是否已签 / 下一档奖励
     */
    CheckinStatusResponse getStatus(Long userId);

    /**
     * 分页查询签到流水, 倒序按签到日期排列
     */
    PageResult<CheckinLog> getLogWithPage(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 查询指定月份签到积分规则, 月份为空则取当前月. 找不到该月规则时回退 month=0 的默认配置.
     */
    CheckinRuleMonthResponse getRule(Integer month);

    /**
     * 游标分页查询签到流水，适用于深分页
     */
    CursorPageResult<CheckinLog> getLogWithCursor(Long userId, String cursor, Integer pageSize);
}
