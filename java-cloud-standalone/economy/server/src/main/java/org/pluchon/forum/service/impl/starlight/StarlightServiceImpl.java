package org.pluchon.forum.service.impl.starlight;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.StarlightLog;
import org.pluchon.forum.entity.db.UserStarlightWallet;
import org.pluchon.forum.mapper.StarlightLogMapper;
import org.pluchon.forum.mapper.UserStarlightWalletMapper;
import org.pluchon.forum.service.interfaces.starlight.StarlightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

// 萌星辉钱包实现：行锁 + 乐观版本 + 幂等流水
@Service
public class StarlightServiceImpl implements StarlightService {

    public static final byte SOURCE_DRAW = 1;
    public static final byte SOURCE_EXCHANGE = 2;
    public static final byte SOURCE_CHECKIN = 3;

    private static final int AMOUNT_SSR = 50;
    private static final int AMOUNT_SR = 15;
    private static final int AMOUNT_R = 5;
    private static final int AMOUNT_NORMAL = 1;

    @Autowired
    private UserStarlightWalletMapper userStarlightWalletMapper;

    @Autowired
    private StarlightLogMapper starlightLogMapper;

    @Override
    public int getBalance(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        UserStarlightWallet wallet = userStarlightWalletMapper.selectByUserId(userId);
        if (wallet == null || wallet.getBalance() == null) {
            return 0;
        }
        return Math.max(0, wallet.getBalance());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void credit(Long userId, int amount, byte sourceType, Long relatedId, String idempotencyKey, String remark) {
        if (userId == null || userId <= 0 || amount <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请求无效，请刷新后重试"));
        }
        StarlightLog existing = findByIdempotency(userId, idempotencyKey);
        if (existing != null) {
            if (existing.getBalanceAfter() == null) {
                getBalance(userId);
            }
            return;
        }
        UserStarlightWallet wallet = ensureForUpdate(userId);
        int before = wallet.getBalance() == null ? 0 : wallet.getBalance();
        int after = before + amount;
        int updated = userStarlightWalletMapper.updateBalanceOptimistic(userId, after, wallet.getVersion());
        if (updated != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "萌星辉入账失败，请重试"));
        }
        insertLog(userId, amount, after, sourceType, relatedId, idempotencyKey, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int debit(Long userId, int amount, byte sourceType, Long relatedId, String idempotencyKey, String remark) {
        if (userId == null || userId <= 0 || amount <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请求无效，请刷新后重试"));
        }
        StarlightLog existing = findByIdempotency(userId, idempotencyKey);
        if (existing != null) {
            return existing.getBalanceAfter() == null ? getBalance(userId) : existing.getBalanceAfter();
        }
        UserStarlightWallet wallet = ensureForUpdate(userId);
        int before = wallet.getBalance() == null ? 0 : wallet.getBalance();
        if (before < amount) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "萌星辉不足"));
        }
        int after = before - amount;
        int updated = userStarlightWalletMapper.updateBalanceOptimistic(userId, after, wallet.getVersion());
        if (updated != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "萌星辉扣减失败，请重试"));
        }
        insertLog(userId, -amount, after, sourceType, relatedId, idempotencyKey, remark);
        return after;
    }

    @Override
    public int amountForPrize(Byte isJackpot, Byte prizeType) {
        // 与前端 LotteryView.mapRarity 对齐
        if ((isJackpot != null && isJackpot == 1) || Objects.equals(prizeType, Constant.LOTTERY_PRIZE_GRAND)) {
            return AMOUNT_SSR;
        }
        if (Objects.equals(prizeType, Constant.LOTTERY_PRIZE_SMALL)
                || Objects.equals(prizeType, Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            return AMOUNT_SR;
        }
        if (Objects.equals(prizeType, Constant.LOTTERY_PRIZE_CONSOLATION)
                || Objects.equals(prizeType, Constant.LOTTERY_PRIZE_POINTS)) {
            return AMOUNT_R;
        }
        return AMOUNT_NORMAL;
    }

    private UserStarlightWallet ensureForUpdate(Long userId) {
        UserStarlightWallet locked = userStarlightWalletMapper.selectByUserIdForUpdate(userId);
        if (locked != null) {
            return locked;
        }
        try {
            userStarlightWalletMapper.insertWallet(userId);
        } catch (DuplicateKeyException ignored) {
            // 并发创建
        }
        locked = userStarlightWalletMapper.selectByUserIdForUpdate(userId);
        if (locked == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "萌星辉账户初始化失败，请稍后再试"));
        }
        return locked;
    }

    private StarlightLog findByIdempotency(Long userId, String idempotencyKey) {
        return starlightLogMapper.selectOne(Wrappers.lambdaQuery(StarlightLog.class)
                .eq(StarlightLog::getUserId, userId)
                .eq(StarlightLog::getIdempotencyKey, idempotencyKey));
    }

    private void insertLog(Long userId, int delta, int balanceAfter, byte sourceType,
                           Long relatedId, String idempotencyKey, String remark) {
        StarlightLog logRow = new StarlightLog();
        logRow.setUserId(userId);
        logRow.setDelta(delta);
        logRow.setBalanceAfter(balanceAfter);
        logRow.setSourceType(sourceType);
        logRow.setRelatedId(relatedId);
        logRow.setIdempotencyKey(idempotencyKey);
        logRow.setRemark(remark);
        try {
            starlightLogMapper.insert(logRow);
        } catch (DuplicateKeyException ex) {
            // 并发幂等：忽略重复写入
        }
    }
}
