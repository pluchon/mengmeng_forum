package org.pluchon.forum.service.interfaces.points;

import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.dto.points.PointsLogQueryDTO;
import org.pluchon.forum.entity.vo.points.PointsCenterOverviewVO;
import org.pluchon.forum.entity.vo.points.PointsCenterChartVO;
import org.pluchon.forum.entity.vo.points.PointsCenterTrendVO;
import org.pluchon.forum.entity.vo.points.PointsLogVO;
import org.pluchon.forum.entity.vo.points.PointsWalletVO;

// 积分钱包统一入口. 任何 points_wallet 变动都必须经此服务, 同时落 points_log. 所有 add/deduct 方法都是原子操作: SQL 层 WHERE 条件保证扣减不会出现负余额.
public interface PointsService {

    int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark);

    int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark);

    // 带幂等键的加积分。idempotencyKey 为空时与 {@link #addPoints} 行为一致
    int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey);

    // 带幂等键的扣积分。idempotencyKey 为空时与 {@link #deductPoints} 行为一致
    int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey);

    // 是否已存在相同幂等键的成功流水 用于整单重试短路
    boolean hasIdempotencyRecord(Long userId, String idempotencyKey);

    PointsWalletVO getWallet(Long userId);

    PointsCenterOverviewVO getCenterOverview(Long userId, Integer weekOffset);

    PointsCenterTrendVO getCenterTrend(Long userId, Integer weekOffset);

    PointsCenterChartVO getCenterChart(Long userId, PointsLogQueryDTO query);

    PageResult<PointsLogVO> getCenterLogWithPage(Long userId, PointsLogQueryDTO query);

    int claimMilestone(Long userId, String milestoneCode);
}
