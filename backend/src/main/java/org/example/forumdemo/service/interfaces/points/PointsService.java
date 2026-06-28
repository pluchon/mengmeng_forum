package org.example.forumdemo.service.interfaces.points;

import org.example.forumdemo.entity.vo.common.CursorPageResult;
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

    int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark);

    int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark);

    /**
     * 带幂等键的加积分。idempotencyKey 为空时与 {@link #addPoints} 行为一致。
     */
    int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey);

    /**
     * 带幂等键的扣积分。idempotencyKey 为空时与 {@link #deductPoints} 行为一致。
     */
    int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey);

    /** 是否已存在相同幂等键的成功流水（用于整单重试短路） */
    boolean hasIdempotencyRecord(Long userId, String idempotencyKey);

    PointsWalletVO getWallet(Long userId);

    PageResult<PointsLogVO> getLogWithPage(Long userId, Integer pageNum, Integer pageSize, Byte sourceType);

    /** 游标分页积分流水，适用于深分页 */
    CursorPageResult<PointsLogVO> getLogWithCursor(Long userId, String cursor, Integer pageSize, Byte sourceType);

    List<PointsDailyVO> getDailyAggregation(Long userId, Integer days);
}
