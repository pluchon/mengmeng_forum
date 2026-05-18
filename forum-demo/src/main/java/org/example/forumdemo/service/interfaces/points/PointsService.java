package org.example.forumdemo.service.interfaces.points;

import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.points.PointsDailyVO;
import org.example.forumdemo.entity.vo.points.PointsLogVO;
import org.example.forumdemo.entity.vo.points.PointsWalletVO;

import java.util.List;

/**
 * 积分钱包统一入口. 任何 user.points 变动都必须经此服务, 同时落 points_log.
 * 所有 add/deduct 方法都是原子操作: SQL 层 WHERE 条件保证扣减不会出现负余额.
 */
public interface PointsService {

    /**
     * 原子加积分 + 写流水. amount 必须 > 0; 失败抛 ApplicationException.
     * @return 变动后余额
     */
    int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark);

    /**
     * 原子扣积分 + 写流水. amount 必须 > 0; 余额不足直接抛 FAILED_POINTS_NOT_ENOUGH.
     * @return 变动后余额
     */
    int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark);

    /**
     * 钱包概览(余额 + 累计签到入账 + 累计商城消费), 用于"我的积分"页头部展示.
     */
    PointsWalletVO getWallet(Long userId);

    /**
     * 分页查询积分流水.
     */
    PageResult<PointsLogVO> getLogWithPage(Long userId, Integer pageNum, Integer pageSize, Byte sourceType);

    /**
     * 最近 N 天按自然日聚合, 给 ECharts 用. days 默认 30, 上限 365.
     */
    List<PointsDailyVO> getDailyAggregation(Long userId, Integer days);
}
