package org.example.forumdemo.service.impl.points;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.PointsLog;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.points.PointsDailyVO;
import org.example.forumdemo.entity.vo.points.PointsLogVO;
import org.example.forumdemo.entity.vo.points.PointsWalletVO;
import org.example.forumdemo.mapper.PointsLogMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 积分钱包统一实现.
 *
 * 写路径 (addPoints / deductPoints):
 *   1) 调用 UserMapper 的原子 SQL 加 / 减 user.points; 扣减失败(行数=0)直接抛余额不足
 *   2) 再 SELECT 一次最新余额 (balance_after 快照)
 *   3) INSERT points_log
 *   写操作整体在 @Transactional 内, 任一步失败回滚, 不会出现"加积分了但流水没记上"的情况.
 *
 * 读路径 (getWallet / getLogWithPage / getDailyAggregation):
 *   - getWallet     : SELECT user.points + 两次 SUM(points_log), 量很小, 不上缓存
 *   - getLogWithPage: 标准 MyBatis-Plus 分页, 用户主动翻页才查询, 不上缓存
 *   - 按日聚合      : 走 PointsLogMapper.selectDailyAggregation, 单用户范围最多 365 行, 不上缓存
 */
@Service
@Slf4j
public class PointsServiceImpl implements PointsService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        if (userId == null || userId <= 0 || amount <= 0 || sourceType == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int affected = userMapper.addPoints(userId, amount);
        if (affected != 1) {
            log.warn("加积分失败: userId={}, amount={}, affected={}", userId, amount, affected);
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        int balanceAfter = selectBalance(userId);
        insertLog(userId, amount, balanceAfter, sourceType, relatedId, remark);
        return balanceAfter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        if (userId == null || userId <= 0 || amount <= 0 || sourceType == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int affected = userMapper.deductPoints(userId, amount);
        if (affected != 1) {
            // 0 行 = 用户不存在 / 已删除 / 余额不足; 业务侧主要意图是余额不足提示
            throw new ApplicationException(Result.fail(ResultCode.FAILED_POINTS_NOT_ENOUGH));
        }
        int balanceAfter = selectBalance(userId);
        insertLog(userId, -amount, balanceAfter, sourceType, relatedId, remark);
        return balanceAfter;
    }

    @Override
    public PointsWalletVO getWallet(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int balance = selectBalance(userId);
        int totalCheckin = sumByPositiveSources(userId, Constant.POINTS_SOURCE_CHECKIN_BASIC,
                Constant.POINTS_SOURCE_CHECKIN_BONUS);
        int totalSpend = absSumByNegativeSources(userId, Constant.POINTS_SOURCE_SHOP_PURCHASE);
        return new PointsWalletVO(balance, totalCheckin, totalSpend);
    }

    @Override
    public PageResult<PointsLogVO> getLogWithPage(Long userId, Integer pageNum, Integer pageSize, Byte sourceType) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<PointsLog> page = new Page<>(validPageNum, validPageSize);
        var qw = new QueryWrapper<PointsLog>().lambda()
                .eq(PointsLog::getUserId, userId)
                .ne(PointsLog::getDeleteState, 1);
        if (sourceType != null) {
            qw.eq(PointsLog::getSourceType, sourceType);
        }
        Page<PointsLog> result = pointsLogMapper.selectPage(page, qw
                .orderByDesc(PointsLog::getCreateTime)
                .orderByDesc(PointsLog::getId));
        List<PointsLogVO> records = new ArrayList<>(result.getRecords().size());
        for (PointsLog row : result.getRecords()) {
            records.add(new PointsLogVO(row.getId(), row.getDelta(), row.getBalanceAfter(),
                    row.getSourceType(), row.getRelatedId(), row.getRemark(), row.getCreateTime()));
        }
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public List<PointsDailyVO> getDailyAggregation(Long userId, Integer days) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validDays = (days == null || days < 1) ? DEFAULT_DAYS : Math.min(days, MAX_DAYS);
        LocalDate today = LocalDate.now(SHANGHAI);
        Date from = Date.from(today.minusDays(validDays - 1L).atStartOfDay(SHANGHAI).toInstant());
        Date to = Date.from(today.plusDays(1).atStartOfDay(SHANGHAI).toInstant());
        List<Map<String, Object>> raw = pointsLogMapper.selectDailyAggregation(userId, from, to);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        List<PointsDailyVO> list = new ArrayList<>(raw.size());
        for (Map<String, Object> row : raw) {
            Object dayObj = row.get("day");
            String day = dayObj instanceof Date ? fmt.format((Date) dayObj) : String.valueOf(dayObj);
            list.add(new PointsDailyVO(day,
                    toInt(row.get("in_total")),
                    toInt(row.get("out_total")),
                    toInt(row.get("net"))));
        }
        return list;
    }

    private int selectBalance(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user.getPoints() == null ? 0 : user.getPoints();
    }

    private void insertLog(Long userId, int delta, int balanceAfter, Byte sourceType,
                           Long relatedId, String remark) {
        PointsLog row = new PointsLog();
        row.setUserId(userId);
        row.setDelta(delta);
        row.setBalanceAfter(balanceAfter);
        row.setSourceType(sourceType);
        row.setRelatedId(relatedId);
        row.setRemark(remark);
        pointsLogMapper.insert(row);
    }

    /** SUM(delta) WHERE source IN (...) AND delta > 0, 单数返回 0 */
    private int sumByPositiveSources(Long userId, Byte... sources) {
        QueryWrapper<PointsLog> qw = new QueryWrapper<>();
        qw.select("COALESCE(SUM(delta),0) AS total")
                .eq("user_id", userId).eq("delete_state", 0)
                .gt("delta", 0)
                .in("source_type", (Object[]) sources);
        Map<String, Object> map = pointsLogMapper.selectMaps(qw).stream().findFirst().orElse(null);
        return map == null ? 0 : toInt(map.get("total"));
    }

    /** ABS(SUM(delta)) WHERE source IN (...) AND delta < 0, 单数返回 0 */
    private int absSumByNegativeSources(Long userId, Byte... sources) {
        QueryWrapper<PointsLog> qw = new QueryWrapper<>();
        qw.select("COALESCE(SUM(-delta),0) AS total")
                .eq("user_id", userId).eq("delete_state", 0)
                .lt("delta", 0)
                .in("source_type", (Object[]) sources);
        Map<String, Object> map = pointsLogMapper.selectMaps(qw).stream().findFirst().orElse(null);
        return map == null ? 0 : toInt(map.get("total"));
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }
}
