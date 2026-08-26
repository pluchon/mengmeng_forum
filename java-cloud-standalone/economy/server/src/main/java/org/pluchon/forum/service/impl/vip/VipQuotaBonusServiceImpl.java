package org.pluchon.forum.service.impl.vip;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.entity.db.VipQuotaBonusGrant;
import org.pluchon.forum.api.economy.VipBonusReservationVO;
import org.pluchon.forum.api.economy.VipBonusSettlementVO;
import org.pluchon.forum.mapper.VipQuotaBonusGrantMapper;
import org.pluchon.forum.service.interfaces.vip.VipQuotaBonusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

// 体验卡礼包按 PRO 月额度线性折算，独立于用户现有基础额度
@Service
public class VipQuotaBonusServiceImpl implements VipQuotaBonusService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Autowired
    private VipQuotaBonusGrantMapper bonusGrantMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipQuotaBonusGrant grantTrialBonus(Long userId, int proDays, String sourceType, String idempotencyKey) {
        if (userId == null || proDays <= 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        VipQuotaBonusGrant existing = bonusGrantMapper.selectOne(new LambdaQueryWrapper<VipQuotaBonusGrant>()
                .eq(VipQuotaBonusGrant::getUserId, userId)
                .eq(VipQuotaBonusGrant::getSourceIdempotencyKey, idempotencyKey)
                .eq(VipQuotaBonusGrant::getDeleteState, 0));
        if (existing != null) {
            return existing;
        }
        BigDecimal dayRatio = BigDecimal.valueOf(proDays)
                .divide(BigDecimal.valueOf(30), 8, RoundingMode.HALF_UP);
        VipQuotaBonusGrant grant = new VipQuotaBonusGrant();
        grant.setUserId(userId);
        grant.setSourceType(sourceType == null || sourceType.isBlank() ? "TRIAL_VIP" : sourceType);
        grant.setSourceIdempotencyKey(idempotencyKey);
        grant.setQwenGrantedMicros(new BigDecimal("10900000").multiply(dayRatio).longValue());
        grant.setQwenUsedMicros(0L);
        grant.setQwenReservedMicros(0L);
        grant.setWanGrantedCredits(new BigDecimal("20").multiply(dayRatio).setScale(4, RoundingMode.HALF_UP));
        grant.setWanUsedCredits(BigDecimal.ZERO.setScale(4));
        grant.setWanReservedCredits(BigDecimal.ZERO.setScale(4));
        grant.setExpireTime(Date.from(ZonedDateTime.now(TAIPEI).plusDays(30).toInstant()));
        grant.setDeleteState(0);
        try {
            bonusGrantMapper.insert(grant);
            return grant;
        } catch (DuplicateKeyException ignored) {
            return bonusGrantMapper.selectOne(new LambdaQueryWrapper<VipQuotaBonusGrant>()
                    .eq(VipQuotaBonusGrant::getUserId, userId)
                    .eq(VipQuotaBonusGrant::getSourceIdempotencyKey, idempotencyKey)
                    .eq(VipQuotaBonusGrant::getDeleteState, 0));
        }
    }

    @Override
    public List<VipQuotaBonusGrant> listActiveGrants(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return bonusGrantMapper.selectList(new LambdaQueryWrapper<VipQuotaBonusGrant>()
                .eq(VipQuotaBonusGrant::getUserId, userId)
                .eq(VipQuotaBonusGrant::getDeleteState, 0)
                .gt(VipQuotaBonusGrant::getExpireTime, new Date())
                .orderByAsc(VipQuotaBonusGrant::getExpireTime)
                .orderByAsc(VipQuotaBonusGrant::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipBonusReservationVO reserve(Long userId, String resourceType, BigDecimal amount) {
        String type = normalizeResourceType(resourceType);
        BigDecimal requested = normalizeAmount(amount);
        VipBonusReservationVO result = new VipBonusReservationVO();
        result.setFullyReserved(false);
        result.setReservedAmount(BigDecimal.ZERO);
        if (userId == null || requested.signum() <= 0) {
            return result;
        }
        List<VipQuotaBonusGrant> grants = bonusGrantMapper.selectActiveForUpdate(userId);
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal remaining = requested;
        for (VipQuotaBonusGrant grant : grants) {
            BigDecimal available = available(grant, type);
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal take = available.min(remaining);
            allocations.add(new Allocation(grant.getId(), take));
            remaining = remaining.subtract(take);
            if (remaining.signum() <= 0) {
                break;
            }
        }
        if (remaining.signum() > 0) {
            return result;
        }
        for (Allocation allocation : allocations) {
            VipQuotaBonusGrant grant = grants.stream()
                    .filter(item -> item.getId().equals(allocation.id))
                    .findFirst()
                    .orElseThrow();
            addReserved(grant, type, allocation.amount);
            bonusGrantMapper.updateById(grant);
        }
        result.setFullyReserved(true);
        result.setReservedAmount(requested);
        result.setReservationToken(encodeToken(userId, type, allocations));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipBonusSettlementVO settle(Long userId, String reservationToken, BigDecimal actualAmount) {
        ReservationToken token = decodeToken(reservationToken);
        requireTokenUser(userId, token);
        BigDecimal remainingActual = normalizeAmount(actualAmount);
        BigDecimal consumed = BigDecimal.ZERO;
        for (Allocation allocation : token.allocations) {
            VipQuotaBonusGrant grant = bonusGrantMapper.selectByIdForUpdate(allocation.id, userId);
            if (grant == null) {
                continue;
            }
            BigDecimal currentReserved = reserved(grant, token.resourceType);
            BigDecimal releasable = allocation.amount.min(currentReserved);
            BigDecimal use = releasable.min(remainingActual);
            settleAllocation(grant, token.resourceType, releasable, use);
            bonusGrantMapper.updateById(grant);
            consumed = consumed.add(use);
            remainingActual = remainingActual.subtract(use).max(BigDecimal.ZERO);
        }
        VipBonusSettlementVO result = new VipBonusSettlementVO();
        result.setConsumedAmount(consumed);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long userId, String reservationToken) {
        ReservationToken token = decodeToken(reservationToken);
        requireTokenUser(userId, token);
        for (Allocation allocation : token.allocations) {
            VipQuotaBonusGrant grant = bonusGrantMapper.selectByIdForUpdate(allocation.id, userId);
            if (grant == null) {
                continue;
            }
            BigDecimal releasable = allocation.amount.min(reserved(grant, token.resourceType));
            settleAllocation(grant, token.resourceType, releasable, BigDecimal.ZERO);
            bonusGrantMapper.updateById(grant);
        }
    }

    private BigDecimal available(VipQuotaBonusGrant grant, String type) {
        return switch (type) {
            case "QWEN" -> BigDecimal.valueOf(Math.max(0L,
                    value(grant.getQwenGrantedMicros()) - value(grant.getQwenUsedMicros())
                            - value(grant.getQwenReservedMicros())));
            case "WAN" -> decimal(grant.getWanGrantedCredits())
                    .subtract(decimal(grant.getWanUsedCredits()))
                    .subtract(decimal(grant.getWanReservedCredits())).max(BigDecimal.ZERO);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal reserved(VipQuotaBonusGrant grant, String type) {
        return switch (type) {
            case "QWEN" -> BigDecimal.valueOf(value(grant.getQwenReservedMicros()));
            case "WAN" -> decimal(grant.getWanReservedCredits());
            default -> BigDecimal.ZERO;
        };
    }

    private void addReserved(VipQuotaBonusGrant grant, String type, BigDecimal amount) {
        switch (type) {
            case "QWEN" -> grant.setQwenReservedMicros(value(grant.getQwenReservedMicros()) + amount.longValueExact());
            case "WAN" -> grant.setWanReservedCredits(decimal(grant.getWanReservedCredits()).add(amount));
            default -> throw new IllegalArgumentException("不支持的礼包资源类型");
        }
    }

    private void settleAllocation(VipQuotaBonusGrant grant, String type, BigDecimal releaseAmount,
                                  BigDecimal usedAmount) {
        switch (type) {
            case "QWEN" -> {
                grant.setQwenReservedMicros(Math.max(0L,
                        value(grant.getQwenReservedMicros()) - releaseAmount.longValue()));
                grant.setQwenUsedMicros(value(grant.getQwenUsedMicros()) + usedAmount.longValue());
            }
            case "WAN" -> {
                grant.setWanReservedCredits(decimal(grant.getWanReservedCredits())
                        .subtract(releaseAmount).max(BigDecimal.ZERO));
                grant.setWanUsedCredits(decimal(grant.getWanUsedCredits()).add(usedAmount));
            }
            default -> throw new IllegalArgumentException("不支持的礼包资源类型");
        }
    }

    private String encodeToken(Long userId, String type, List<Allocation> allocations) {
        String joined = allocations.stream()
                .map(item -> item.id + ":" + item.amount.toPlainString())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String raw = userId + "|" + type + "|" + joined;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ReservationToken decodeToken(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            List<Allocation> allocations = new ArrayList<>();
            if (parts.length == 3 && !parts[2].isBlank()) {
                for (String item : parts[2].split(",")) {
                    String[] pair = item.split(":", 2);
                    allocations.add(new Allocation(Long.parseLong(pair[0]), new BigDecimal(pair[1])));
                }
            }
            return new ReservationToken(Long.parseLong(parts[0]), normalizeResourceType(parts[1]), allocations);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无效的礼包预占凭据", exception);
        }
    }

    private void requireTokenUser(Long userId, ReservationToken token) {
        if (userId == null || token == null || !userId.equals(token.userId)) {
            throw new IllegalArgumentException("礼包预占凭据不属于当前用户");
        }
    }

    private String normalizeResourceType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("QWEN", "WAN").contains(type)) {
            throw new IllegalArgumentException("不支持的礼包资源类型");
        }
        return type;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    private long value(Long number) {
        return number == null ? 0L : number;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private BigDecimal decimal(BigDecimal number) {
        return number == null ? BigDecimal.ZERO : number;
    }

    private record Allocation(Long id, BigDecimal amount) {
    }

    private record ReservationToken(Long userId, String resourceType, List<Allocation> allocations) {
    }
}
