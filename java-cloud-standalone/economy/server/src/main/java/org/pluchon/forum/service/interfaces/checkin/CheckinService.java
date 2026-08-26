package org.pluchon.forum.service.interfaces.checkin;

import org.pluchon.forum.entity.db.CheckinLog;
import org.pluchon.forum.entity.vo.checkin.CheckinMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinResultResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinRuleMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinStatusResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinLogVO;
import org.pluchon.forum.entity.vo.common.CursorPageResult;
import org.pluchon.forum.entity.vo.common.PageResult;

// 签到模块
public interface CheckinService {

    // 执行签到, 一次返回本次得分 + 签后最新状态
    CheckinResultResponse doCheckin(Long userId);

    // 查询当前签到状态快照
    CheckinStatusResponse getStatus(Long userId);

    // 分页查询签到流水
    PageResult<CheckinLogVO> getLogWithPage(Long userId, Integer pageNum, Integer pageSize);

    // 查询指定月份签到积分规则
    CheckinRuleMonthResponse getRule(Integer month);

    // 游标分页查询签到流水
    CursorPageResult<CheckinLog> getLogWithCursor(Long userId, String cursor, Integer pageSize);

    // 查询月度签到摘要 日历 + 周统计
    CheckinMonthResponse getMonth(Long userId, Integer year, Integer month);

    // 消耗补签卡，自动补签离今天最近的漏签日 日期由服务端决定，防自选刷奖
    CheckinResultResponse makeupCheckin(Long userId);

    // 发放补签卡（商城兑换等外部入账）
    void grantMakeupCards(Long userId, int amount);
}
