package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.LotteryPrizeCatalogStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.util.AdminPagination;
import org.example.forumdemo.entity.db.LotteryActivity;
import org.example.forumdemo.entity.db.LotteryActivityPrize;
import org.example.forumdemo.entity.db.LotteryDrawRecord;
import org.example.forumdemo.entity.db.LotteryPrize;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivityMetaUpdateRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivityPhaseRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivitySaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeLineSaveDTO;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminLotteryActivityDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryActivityRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryDrawUserRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeLineVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryWinRowVO;
import org.example.forumdemo.entity.vo.admin.LotteryDrawUserAggRow;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.lottery.LotteryPrizePoolRow;
import org.example.forumdemo.mapper.LotteryActivityMapper;
import org.example.forumdemo.mapper.LotteryActivityPrizeMapper;
import org.example.forumdemo.mapper.LotteryDrawRecordMapper;
import org.example.forumdemo.mapper.LotteryPrizeMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.admin.AdminLotteryActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminLotteryActivityServiceImpl implements AdminLotteryActivityService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private LotteryActivityMapper lotteryActivityMapper;

    @Resource
    private LotteryActivityPrizeMapper lotteryActivityPrizeMapper;

    @Resource
    private LotteryPrizeMapper lotteryPrizeMapper;

    @Resource
    private LotteryDrawRecordMapper lotteryDrawRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public PageResult<AdminLotteryActivityRowVO> pageActivities(Integer page, Integer size, Integer pageNum,
                                                                Integer pageSize, String title, Integer phase,
                                                                Integer deleteState, String sortBy, String sortOrder) {
        Page<LotteryActivity> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<LotteryActivity> w = Wrappers.lambdaQuery(LotteryActivity.class);
        if (deleteState != null) {
            w.eq(LotteryActivity::getDeleteState, deleteState.byteValue());
        } else {
            w.eq(LotteryActivity::getDeleteState, (byte) 0);
        }
        if (phase != null) {
            w.eq(LotteryActivity::getPhase, phase.byteValue());
        }
        if (StringUtils.hasText(title)) {
            w.like(LotteryActivity::getTitle, title.trim());
        }
        applyListOrder(w, sortBy, sortOrder);
        Page<LotteryActivity> result = lotteryActivityMapper.selectPage(p, w);
        List<AdminLotteryActivityRowVO> rows = result.getRecords().stream().map(this::toRow).toList();
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    public AdminLotteryActivityDetailVO detail(Long id) {
        if (id == null || id <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LotteryActivity a = lotteryActivityMapper.selectById(id);
        if (a == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        AdminLotteryActivityDetailVO vo = toDetail(a);
        List<LotteryPrizePoolRow> pool = lotteryActivityPrizeMapper.selectPool(id);
        List<AdminLotteryPrizeLineVO> lines = new ArrayList<>();
        for (LotteryPrizePoolRow r : pool) {
            AdminLotteryPrizeLineVO pl = new AdminLotteryPrizeLineVO();
            pl.setActivityPrizeId(r.getActivityPrizeId());
            pl.setPrizeId(r.getPrizeId());
            pl.setName(r.getPrizeName());
            pl.setPrizeType(r.getPrizeType());
            pl.setPrizeValue(r.getPrizeValue());
            pl.setWeight(r.getWeight());
            pl.setStockRemaining(r.getStockRemaining());
            pl.setIsJackpot(r.getIsJackpot());
            pl.setImagePath(r.getImagePath());
            pl.setIsMysteryBundle(r.getIsMysteryBundle());
            pl.setCatalogStatus(r.getCatalogStatus());
            lines.add(pl);
        }
        vo.setPrizeLines(lines);
        return vo;
    }

    @Override
    public PageResult<AdminLotteryWinRowVO> pageWins(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                     Long activityId, Long userId, Integer prizeType) {
        if (activityId == null || activityId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Page<LotteryDrawRecord> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<LotteryDrawRecord> w = Wrappers.lambdaQuery(LotteryDrawRecord.class)
                .eq(LotteryDrawRecord::getActivityId, activityId)
                .ne(LotteryDrawRecord::getDeleteState, (byte) 1);
        if (userId != null && userId > 0) {
            w.eq(LotteryDrawRecord::getUserId, userId);
        }
        if (prizeType != null) {
            w.eq(LotteryDrawRecord::getPrizeType, prizeType.byteValue());
        }
        w.orderByDesc(LotteryDrawRecord::getCreateTime);
        Page<LotteryDrawRecord> result = lotteryDrawRecordMapper.selectPage(p, w);
        List<Long> uids = result.getRecords().stream().map(LotteryDrawRecord::getUserId).distinct().toList();
        Map<Long, User> users = Map.of();
        if (!uids.isEmpty()) {
            List<User> us = userMapper.selectList(Wrappers.lambdaQuery(User.class).in(User::getId, uids));
            users = us.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }
        Map<Long, User> finalUsers = users;
        List<AdminLotteryWinRowVO> rows = result.getRecords().stream().map(r -> toWinRow(r, finalUsers)).toList();
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    public PageResult<AdminLotteryDrawUserRowVO> pageDrawUsers(Integer page, Integer size, Integer pageNum,
                                                               Integer pageSize, Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Page<?> p = AdminPagination.of(page, size, pageNum, pageSize);
        long total = lotteryDrawRecordMapper.countDistinctDrawUsers(activityId);
        long offset = (p.getCurrent() - 1) * p.getSize();
        List<LotteryDrawUserAggRow> aggs = lotteryDrawRecordMapper.selectDrawUserPage(
                activityId, offset, p.getSize());
        List<Long> uids = aggs.stream().map(LotteryDrawUserAggRow::getUserId).filter(Objects::nonNull).toList();
        Map<Long, User> users = Map.of();
        if (!uids.isEmpty()) {
            List<User> us = userMapper.selectList(Wrappers.lambdaQuery(User.class).in(User::getId, uids));
            users = us.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }
        Map<Long, User> finalUsers = users;
        List<AdminLotteryDrawUserRowVO> rows = aggs.stream().map(a -> toDrawUserRow(a, finalUsers)).toList();
        long pages = p.getSize() > 0 ? (total + p.getSize() - 1) / p.getSize() : 0;
        boolean hasNext = p.getCurrent() < pages;
        return new PageResult<>(rows, total, (int) p.getCurrent(), (int) p.getSize(), pages, hasNext);
    }

    private AdminLotteryDrawUserRowVO toDrawUserRow(LotteryDrawUserAggRow a, Map<Long, User> users) {
        AdminLotteryDrawUserRowVO vo = new AdminLotteryDrawUserRowVO();
        vo.setUserId(a.getUserId());
        vo.setDrawCount(a.getDrawCount());
        User u = users.get(a.getUserId());
        if (u != null) {
            vo.setNickname(StringUtils.hasText(u.getNickname()) ? u.getNickname() : "");
            vo.setAvatarUrl(u.getAvatarUrl());
            vo.setVipTier(u.getVipTier());
            if (u.getVipExpireAt() != null) {
                synchronized (DF) {
                    vo.setVipExpireAt(DF.format(u.getVipExpireAt()));
                }
            }
        } else {
            vo.setNickname("");
        }
        if (a.getLastDrawTime() != null) {
            synchronized (DF) {
                vo.setLastDrawTime(DF.format(a.getLastDrawTime()));
            }
        } else {
            vo.setLastDrawTime("");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AdminLotteryActivitySaveRequest body, Long operatorUserId) {
        validateSave(body);
        LotteryActivity act = new LotteryActivity();
        act.setTitle(body.getTitle().trim());
        act.setDescription(body.getDescription());
        act.setCoverImageUrl(trimPath(body.getCoverImageUrl()));
        act.setCostPointsPerDraw(body.getCostPointsPerDraw() == null ? 30 : body.getCostPointsPerDraw());
        act.setStatus(body.getStatus() == null ? (byte) 1 : body.getStatus());
        act.setPhase(body.getPhase() == null ? (byte) 0 : body.getPhase());
        act.setStartTime(body.getStartTime());
        act.setEndTime(body.getEndTime());

        Long activityId;
        if (body.getId() == null) {
            act.setDeleteState((byte) 0);
            if (operatorUserId != null && operatorUserId > 0) {
                act.setPublisherId(operatorUserId);
            }
            lotteryActivityMapper.insert(act);
            activityId = act.getId();
        } else {
            LotteryActivity existing = lotteryActivityMapper.selectById(body.getId());
            if (existing == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
            }
            act.setId(body.getId());
            act.setDeleteState(existing.getDeleteState());
            act.setPublisherId(existing.getPublisherId());
            lotteryActivityMapper.updateById(act);
            activityId = body.getId();
        }

        Set<Long> incomingApIds = body.getLines().stream()
                .map(AdminLotteryPrizeLineSaveDTO::getActivityPrizeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<LotteryActivityPrize> existingLaps = lotteryActivityPrizeMapper.selectList(
                Wrappers.lambdaQuery(LotteryActivityPrize.class)
                        .eq(LotteryActivityPrize::getActivityId, activityId)
                        .eq(LotteryActivityPrize::getDeleteState, (byte) 0));
        for (LotteryActivityPrize lap : existingLaps) {
            if (!incomingApIds.contains(lap.getId())) {
                lotteryActivityPrizeMapper.update(null, new LambdaUpdateWrapper<LotteryActivityPrize>()
                        .set(LotteryActivityPrize::getDeleteState, (byte) 1)
                        .eq(LotteryActivityPrize::getId, lap.getId()));
            }
        }

        for (AdminLotteryPrizeLineSaveDTO line : body.getLines()) {
            if (line.getActivityPrizeId() != null) {
                LotteryActivityPrize lap = lotteryActivityPrizeMapper.selectById(line.getActivityPrizeId());
                if (lap == null || Objects.equals(lap.getDeleteState(), (byte) 1)) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
                }
                if (!lap.getActivityId().equals(activityId)) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
                }
                LotteryPrize prize = lotteryPrizeMapper.selectById(lap.getPrizeId());
                if (prize == null) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
                }
                lap.setWeight(line.getWeight() == null ? 1 : line.getWeight());
                lap.setStockRemaining(line.getStockRemaining() == null ? -1 : line.getStockRemaining());
                lap.setIsJackpot(resolveJackpot(prize.getPrizeType(), line.getIsJackpot()));
                lap.setImagePath(trimPath(line.getImagePath()));
                lap.setDeleteState((byte) 0);
                lotteryActivityPrizeMapper.updateById(lap);
            } else if (line.getPrizeId() != null) {
                LotteryPrize prize = lotteryPrizeMapper.selectById(line.getPrizeId());
                requireOnShelfPrize(prize);
                LotteryActivityPrize lap = new LotteryActivityPrize();
                lap.setActivityId(activityId);
                lap.setPrizeId(prize.getId());
                lap.setWeight(line.getWeight() == null ? 1 : line.getWeight());
                lap.setStockRemaining(line.getStockRemaining() == null ? -1 : line.getStockRemaining());
                lap.setIsJackpot(resolveJackpot(prize.getPrizeType(), line.getIsJackpot()));
                lap.setImagePath(trimPath(line.getImagePath()));
                lap.setDeleteState((byte) 0);
                lotteryActivityPrizeMapper.insert(lap);
            } else {
                if (!StringUtils.hasText(line.getName())) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
                }
                LotteryPrize prize = new LotteryPrize();
                prize.setName(line.getName().trim());
                prize.setPrizeType(line.getPrizeType());
                prize.setPrizeValue(line.getPrizeValue() == null ? 0 : line.getPrizeValue());
                prize.setCatalogStatus(LotteryPrizeCatalogStatus.ON_SHELF.getCode());
                prize.setIsMysteryBundle((byte) 0);
                prize.setImagePath(trimPath(line.getImagePath()));
                prize.setDeleteState((byte) 0);
                lotteryPrizeMapper.insert(prize);

                LotteryActivityPrize lap = new LotteryActivityPrize();
                lap.setActivityId(activityId);
                lap.setPrizeId(prize.getId());
                lap.setWeight(line.getWeight() == null ? 1 : line.getWeight());
                lap.setStockRemaining(line.getStockRemaining() == null ? -1 : line.getStockRemaining());
                lap.setIsJackpot(resolveJackpot(prize.getPrizeType(), line.getIsJackpot()));
                lap.setImagePath(trimPath(line.getImagePath()));
                lap.setDeleteState((byte) 0);
                lotteryActivityPrizeMapper.insert(lap);
            }
        }
        return activityId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMeta(AdminLotteryActivityMetaUpdateRequest body) {
        if (body == null || body.getId() == null || body.getId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!StringUtils.hasText(body.getTitle())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LotteryActivity existing = lotteryActivityMapper.selectById(body.getId());
        if (existing == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        LambdaUpdateWrapper<LotteryActivity> uw = new LambdaUpdateWrapper<LotteryActivity>()
                .eq(LotteryActivity::getId, body.getId())
                .set(LotteryActivity::getTitle, body.getTitle().trim())
                .set(LotteryActivity::getDescription, body.getDescription())
                .set(LotteryActivity::getCoverImageUrl, trimPath(body.getCoverImageUrl()))
                .set(LotteryActivity::getCostPointsPerDraw,
                        body.getCostPointsPerDraw() == null ? 30 : body.getCostPointsPerDraw());
        if (body.getStatus() != null) {
            uw.set(LotteryActivity::getStatus, body.getStatus());
        }
        if (body.getPhase() != null) {
            uw.set(LotteryActivity::getPhase, body.getPhase());
        }
        uw.set(LotteryActivity::getStartTime, body.getStartTime());
        uw.set(LotteryActivity::getEndTime, body.getEndTime());
        lotteryActivityMapper.update(null, uw);
    }

    @Override
    public void patchPhase(AdminLotteryActivityPhaseRequest body) {
        if (body == null || body.getId() == null || body.getId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (body.getPhase() == null && body.getStatus() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LambdaUpdateWrapper<LotteryActivity> uw = new LambdaUpdateWrapper<LotteryActivity>()
                .eq(LotteryActivity::getId, body.getId());
        if (body.getPhase() != null) {
            uw.set(LotteryActivity::getPhase, body.getPhase());
        }
        if (body.getStatus() != null) {
            uw.set(LotteryActivity::getStatus, body.getStatus());
        }
        int n = lotteryActivityMapper.update(null, uw);
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    private void applyListOrder(LambdaQueryWrapper<LotteryActivity> w, String sortBy, String sortOrder) {
        boolean desc = "desc".equalsIgnoreCase(StringUtils.hasText(sortOrder) ? sortOrder.trim() : "");
        if ("createTime".equalsIgnoreCase(StringUtils.hasText(sortBy) ? sortBy.trim() : "")) {
            if (desc) {
                w.orderByDesc(LotteryActivity::getCreateTime).orderByDesc(LotteryActivity::getId);
            } else {
                w.orderByAsc(LotteryActivity::getCreateTime).orderByAsc(LotteryActivity::getId);
            }
            return;
        }
        w.orderByAsc(LotteryActivity::getId);
    }

    @Override
    public void setDeleteState(AdminSetDeleteStateRequest req) {
        if (req.getId() == null || req.getId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (req.getDeleteState() == null || (req.getDeleteState() != 0 && req.getDeleteState() != 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = lotteryActivityMapper.update(null, new LambdaUpdateWrapper<LotteryActivity>()
                .set(LotteryActivity::getDeleteState, req.getDeleteState().byteValue())
                .eq(LotteryActivity::getId, req.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    private void validateSave(AdminLotteryActivitySaveRequest body) {
        if (body == null || !StringUtils.hasText(body.getTitle())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (body.getLines() == null || body.getLines().isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        long grandParents = body.getLines().stream()
                .filter(l -> Constant.LOTTERY_PRIZE_GRAND.equals(effectivePrizeType(l)))
                .count();
        if (grandParents < 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "奖池须包含 1 个「大奖/神秘大奖」档位（奖品类型为大奖）"));
        }
        if (grandParents > 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "奖池仅可配置 1 个「大奖/神秘大奖」档位"));
        }
        if (body.getId() == null) {
            for (AdminLotteryPrizeLineSaveDTO line : body.getLines()) {
                if (line.getActivityPrizeId() != null) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "新建活动不可带 activityPrizeId"));
                }
            }
        }
        for (AdminLotteryPrizeLineSaveDTO line : body.getLines()) {
            int w = line.getWeight() == null ? 1 : line.getWeight();
            if (w < 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            int stock = line.getStockRemaining() == null ? -1 : line.getStockRemaining();
            if (stock < -1) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            Byte effType = effectivePrizeType(line);
            if (effType == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            if (Constant.LOTTERY_PRIZE_GRAND.equals(effType) && stock != -1 && stock < 1) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "大奖库存须 >=1 或为 -1(不限)"));
            }
            if (line.getPrizeId() != null && line.getActivityPrizeId() == null) {
                LotteryPrize p = lotteryPrizeMapper.selectById(line.getPrizeId());
                requireOnShelfPrize(p);
                validatePrizeValueRules(p);
                continue;
            }
            if (line.getActivityPrizeId() != null) {
                continue;
            }
            if (!StringUtils.hasText(line.getName())) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            if (line.getPrizeType() == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            byte t = line.getPrizeType();
            if (t < 0 || t > 5) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            if (Objects.equals(line.getPrizeType(), Constant.LOTTERY_PRIZE_POINTS)) {
                int pv = line.getPrizeValue() == null ? 0 : line.getPrizeValue();
                if (pv <= 0) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "积分奖须填写大于 0 的 prizeValue(积分数)"));
                }
                if (pv > Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                            "单个积分奖不超过 " + Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX));
                }
            }
            if (Objects.equals(line.getPrizeType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
                int pv = line.getPrizeValue() == null ? 0 : line.getPrizeValue();
                if (pv <= 0) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "VIP 奖须填写大于 0 的天数"));
                }
            }
        }
    }

    private Byte effectivePrizeType(AdminLotteryPrizeLineSaveDTO line) {
        if (line.getPrizeId() != null) {
            LotteryPrize p = lotteryPrizeMapper.selectById(line.getPrizeId());
            return p == null ? line.getPrizeType() : p.getPrizeType();
        }
        if (line.getActivityPrizeId() != null) {
            LotteryActivityPrize lap = lotteryActivityPrizeMapper.selectById(line.getActivityPrizeId());
            if (lap == null) {
                return line.getPrizeType();
            }
            LotteryPrize p = lotteryPrizeMapper.selectById(lap.getPrizeId());
            return p == null ? line.getPrizeType() : p.getPrizeType();
        }
        return line.getPrizeType();
    }

    private void requireOnShelfPrize(LotteryPrize prize) {
        if (prize == null || Objects.equals(prize.getDeleteState(), (byte) 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (!LotteryPrizeCatalogStatus.isOnShelf(prize.getCatalogStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "奖品须为上架状态才可加入活动"));
        }
    }

    private void validatePrizeValueRules(LotteryPrize p) {
        if (Objects.equals(p.getPrizeType(), Constant.LOTTERY_PRIZE_POINTS)) {
            int pv = p.getPrizeValue() == null ? 0 : p.getPrizeValue();
            if (pv <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "积分奖品库 prizeValue 须为正整数"));
            }
            if (pv > Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                        "单个积分奖不超过 " + Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX));
            }
        }
        if (Objects.equals(p.getPrizeType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            int pv = p.getPrizeValue() == null ? 0 : p.getPrizeValue();
            if (pv <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "VIP 奖品库须填写大于 0 的天数"));
            }
        }
    }

    private Byte resolveJackpot(Byte prizeType, Byte requested) {
        if (Constant.LOTTERY_PRIZE_GRAND.equals(prizeType)) {
            return (byte) 1;
        }
        return requested != null ? requested : (byte) 0;
    }

    private String trimPath(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private AdminLotteryActivityRowVO toRow(LotteryActivity a) {
        AdminLotteryActivityRowVO vo = new AdminLotteryActivityRowVO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setCoverImageUrl(a.getCoverImageUrl());
        vo.setPublisherId(a.getPublisherId());
        vo.setCostPointsPerDraw(a.getCostPointsPerDraw());
        vo.setStatus(a.getStatus());
        vo.setPhase(a.getPhase());
        vo.setDeleteState(a.getDeleteState());
        if (a.getCreateTime() != null) {
            synchronized (DF) {
                vo.setCreateTime(DF.format(a.getCreateTime()));
            }
        } else {
            vo.setCreateTime("");
        }
        if (a.getUpdateTime() != null) {
            synchronized (DF) {
                vo.setUpdateTime(DF.format(a.getUpdateTime()));
            }
        } else {
            vo.setUpdateTime("");
        }
        return vo;
    }

    private AdminLotteryActivityDetailVO toDetail(LotteryActivity a) {
        AdminLotteryActivityDetailVO vo = new AdminLotteryActivityDetailVO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setDescription(a.getDescription());
        vo.setCoverImageUrl(a.getCoverImageUrl());
        vo.setPublisherId(a.getPublisherId());
        vo.setCostPointsPerDraw(a.getCostPointsPerDraw());
        vo.setStatus(a.getStatus());
        vo.setPhase(a.getPhase());
        vo.setDeleteState(a.getDeleteState());
        vo.setStartTime(a.getStartTime());
        vo.setEndTime(a.getEndTime());
        if (a.getCreateTime() != null) {
            synchronized (DF) {
                vo.setCreateTime(DF.format(a.getCreateTime()));
            }
        } else {
            vo.setCreateTime("");
        }
        if (a.getUpdateTime() != null) {
            synchronized (DF) {
                vo.setUpdateTime(DF.format(a.getUpdateTime()));
            }
        } else {
            vo.setUpdateTime("");
        }
        return vo;
    }

    private AdminLotteryWinRowVO toWinRow(LotteryDrawRecord r, Map<Long, User> users) {
        AdminLotteryWinRowVO vo = new AdminLotteryWinRowVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        User u = users.get(r.getUserId());
        vo.setNickname(u != null && StringUtils.hasText(u.getNickname()) ? u.getNickname() : "");
        vo.setPrizeName(r.getPrizeName());
        vo.setPrizeType(r.getPrizeType());
        vo.setPrizeValue(r.getPrizeValue());
        vo.setGrantPoints(r.getGrantPoints());
        vo.setIsJackpot(r.getIsJackpot());
        if (r.getCreateTime() != null) {
            synchronized (DF) {
                vo.setCreateTime(DF.format(r.getCreateTime()));
            }
        } else {
            vo.setCreateTime("");
        }
        return vo;
    }
}
