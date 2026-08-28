package org.pluchon.forum.service.impl.lottery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.LotteryVoucherLog;
import org.pluchon.forum.entity.db.UserLotteryVoucher;
import org.pluchon.forum.mapper.LotteryVoucherLogMapper;
import org.pluchon.forum.mapper.UserLotteryVoucherMapper;
import org.pluchon.forum.service.interfaces.lottery.LotteryVoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 抽奖抵扣券钱包实现
@Service
public class LotteryVoucherServiceImpl implements LotteryVoucherService {

    @Autowired
    private UserLotteryVoucherMapper userLotteryVoucherMapper;

    @Autowired
    private LotteryVoucherLogMapper lotteryVoucherLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void credit(Long userId, int amount, Long relatedId, String idempotencyKey, String remark, Byte sourceType) {
        if (userId == null || userId <= 0 || amount <= 0) {
            return;
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请求无效，请刷新后重试"));
        }
        LotteryVoucherLog existing = lotteryVoucherLogMapper.selectOne(Wrappers.lambdaQuery(LotteryVoucherLog.class)
                .eq(LotteryVoucherLog::getUserId, userId)
                .eq(LotteryVoucherLog::getIdempotencyKey, idempotencyKey)
                .ne(LotteryVoucherLog::getDeleteState, 1)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        UserLotteryVoucher wallet = ensureVoucherForUpdate(userId);
        int before = wallet.getBalance() == null ? 0 : wallet.getBalance();
        int after = before + amount;
        int version = wallet.getVersion() == null ? 0 : wallet.getVersion();
        int affected = userLotteryVoucherMapper.updateBalanceOptimistic(userId, after, version);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES, "抵扣券发放失败，请重试"));
        }
        Byte source = sourceType == null ? Constant.LOTTERY_VOUCHER_SOURCE_TASK : sourceType;
        insertVoucherLog(userId, amount, after, source, relatedId, idempotencyKey, remark);
    }

    private UserLotteryVoucher ensureVoucherForUpdate(Long userId) {
        UserLotteryVoucher locked = userLotteryVoucherMapper.selectByUserIdForUpdate(userId);
        if (locked != null) {
            return locked;
        }
        try {
            userLotteryVoucherMapper.insertWallet(userId);
        } catch (DuplicateKeyException ignored) {
            // 并发建档
        }
        locked = userLotteryVoucherMapper.selectByUserIdForUpdate(userId);
        if (locked == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return locked;
    }

    private void insertVoucherLog(Long userId, int delta, int balanceAfter, Byte sourceType,
                                  Long relatedId, String idempotencyKey, String remark) {
        LotteryVoucherLog logRow = new LotteryVoucherLog();
        logRow.setUserId(userId);
        logRow.setDelta(delta);
        logRow.setBalanceAfter(balanceAfter);
        logRow.setSourceType(sourceType);
        logRow.setRelatedId(relatedId);
        logRow.setIdempotencyKey(idempotencyKey);
        logRow.setRemark(remark);
        try {
            lotteryVoucherLogMapper.insert(logRow);
        } catch (DuplicateKeyException ignored) {
            // 幂等命中
        }
    }
}
