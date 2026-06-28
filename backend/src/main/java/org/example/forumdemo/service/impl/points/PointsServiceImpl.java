package org.example.forumdemo.service.impl.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.CursorUtils;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.PointsLog;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.common.CursorPageResult;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.points.PointsDailyVO;
import org.example.forumdemo.entity.vo.points.PointsLogVO;
import org.example.forumdemo.entity.vo.points.PointsWalletVO;
import org.example.forumdemo.mapper.PointsLogMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.impl.user.UserDerivedCacheInvalidator;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private UserDerivedCacheInvalidator userDerivedCacheInvalidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        return addPoints(userId, amount, sourceType, relatedId, remark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        return deductPoints(userId, amount, sourceType, relatedId, remark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey) {
        validateAmount(userId, amount, sourceType);
        if (StringUtils.hasText(idempotencyKey)) {
            userMapper.selectByIdForUpdate(userId);
        }
        Integer existingBalance = resolveExistingBalance(userId, idempotencyKey);
        if (existingBalance != null) {
            return existingBalance;
        }
        int affected = userMapper.addPoints(userId, amount);
        if (affected != 1) {
            log.warn("加积分失败: userId={}, amount={}, affected={}", userId, amount, affected);
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        int balanceAfter = selectBalance(userId);
        insertLog(userId, amount, balanceAfter, sourceType, relatedId, remark, idempotencyKey);
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
        return balanceAfter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey) {
        validateAmount(userId, amount, sourceType);
        if (StringUtils.hasText(idempotencyKey)) {
            userMapper.selectByIdForUpdate(userId);
        }
        Integer existingBalance = resolveExistingBalance(userId, idempotencyKey);
        if (existingBalance != null) {
            return existingBalance;
        }
        int affected = userMapper.deductPoints(userId, amount);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_POINTS_NOT_ENOUGH));
        }
        int balanceAfter = selectBalance(userId);
        insertLog(userId, -amount, balanceAfter, sourceType, relatedId, remark, idempotencyKey);
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
        return balanceAfter;
    }

    @Override
    public boolean hasIdempotencyRecord(Long userId, String idempotencyKey) {
        return findByIdempotencyKey(userId, idempotencyKey) != null;
    }

    @Override
    public PointsWalletVO getWallet(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int balance = selectBalance(userId);
        int totalCheckin = sumByPositiveSources(userId, org.example.forumdemo.common.constant.Constant.POINTS_SOURCE_CHECKIN_BASIC,
                org.example.forumdemo.common.constant.Constant.POINTS_SOURCE_CHECKIN_BONUS);
        int totalSpend = absSumByNegativeSources(userId, org.example.forumdemo.common.constant.Constant.POINTS_SOURCE_SHOP_PURCHASE);
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
        var qw = new LambdaQueryWrapper<PointsLog>()
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
    public CursorPageResult<PointsLogVO> getLogWithCursor(Long userId, String cursor, Integer pageSize, Byte sourceType) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int size = PageUtils.getValidPageSize(pageSize);
        LambdaQueryWrapper<PointsLog> wrapper = new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getUserId, userId)
                .ne(PointsLog::getDeleteState, 1);
        if (sourceType != null) {
            wrapper.eq(PointsLog::getSourceType, sourceType);
        }
        if (StringUtils.hasText(cursor)) {
            CursorUtils.CursorToken token = CursorUtils.decode(cursor);
            Date cursorTime = new Date(token.timeMillis());
            wrapper.and(w -> w.lt(PointsLog::getCreateTime, cursorTime)
                    .or(w2 -> w2.eq(PointsLog::getCreateTime, cursorTime)
                            .lt(PointsLog::getId, token.id())));
        }
        wrapper.orderByDesc(PointsLog::getCreateTime).orderByDesc(PointsLog::getId);
        Page<PointsLog> page = new Page<>(1, size + 1, false);
        List<PointsLog> rows = pointsLogMapper.selectPage(page, wrapper).getRecords();
        boolean hasNext = rows.size() > size;
        if (hasNext) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        List<PointsLogVO> records = new ArrayList<>(rows.size());
        for (PointsLog row : rows) {
            records.add(new PointsLogVO(row.getId(), row.getDelta(), row.getBalanceAfter(),
                    row.getSourceType(), row.getRelatedId(), row.getRemark(), row.getCreateTime()));
        }
        String nextCursor = null;
        if (hasNext && !rows.isEmpty()) {
            PointsLog last = rows.get(rows.size() - 1);
            nextCursor = CursorUtils.encode(last.getCreateTime(), last.getId());
        }
        return new CursorPageResult<>(records, nextCursor, hasNext, size);
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

    private void validateAmount(Long userId, int amount, Byte sourceType) {
        if (userId == null || userId <= 0 || amount <= 0 || sourceType == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private Integer resolveExistingBalance(Long userId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        PointsLog existing = findByIdempotencyKey(userId, idempotencyKey.trim());
        if (existing != null) {
            return existing.getBalanceAfter();
        }
        return null;
    }

    private PointsLog findByIdempotencyKey(Long userId, String idempotencyKey) {
        if (userId == null || !StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return pointsLogMapper.selectOne(new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getUserId, userId)
                .eq(PointsLog::getIdempotencyKey, idempotencyKey)
                .ne(PointsLog::getDeleteState, 1)
                .last("LIMIT 1"));
    }

    private int selectBalance(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user.getPoints() == null ? 0 : user.getPoints();
    }

    private void insertLog(Long userId, int delta, int balanceAfter, Byte sourceType, Long relatedId,
                           String remark, String idempotencyKey) {
        PointsLog row = new PointsLog();
        row.setUserId(userId);
        row.setDelta(delta);
        row.setBalanceAfter(balanceAfter);
        row.setSourceType(sourceType);
        row.setRelatedId(relatedId);
        row.setRemark(remark);
        if (StringUtils.hasText(idempotencyKey)) {
            row.setIdempotencyKey(idempotencyKey.trim());
        }
        try {
            pointsLogMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            PointsLog existing = findByIdempotencyKey(userId, idempotencyKey.trim());
            if (existing == null) {
                throw ex;
            }
        }
    }

    private int sumByPositiveSources(Long userId, Byte... sources) {
        return toInt(pointsLogMapper.sumPositiveBySources(userId, sources));
    }

    private int absSumByNegativeSources(Long userId, Byte... sources) {
        return toInt(pointsLogMapper.sumNegativeAbsBySources(userId, sources));
    }

    private static int toInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
