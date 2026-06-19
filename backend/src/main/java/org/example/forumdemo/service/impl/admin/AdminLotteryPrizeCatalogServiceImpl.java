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
import org.example.forumdemo.common.utils.AdminPagination;
import org.example.forumdemo.entity.db.LotteryPrize;
import org.example.forumdemo.entity.db.LotteryPrizeMysteryItem;
import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeCatalogSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeCatalogStatusRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeMysteryItemSaveDTO;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeCatalogDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeCatalogRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeMysteryItemVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeOptionVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.LotteryPrizeMapper;
import org.example.forumdemo.mapper.LotteryPrizeMysteryItemMapper;
import org.example.forumdemo.service.interfaces.admin.AdminLotteryPrizeCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;

@Service
public class AdminLotteryPrizeCatalogServiceImpl implements AdminLotteryPrizeCatalogService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private LotteryPrizeMapper lotteryPrizeMapper;

    @Resource
    private LotteryPrizeMysteryItemMapper lotteryPrizeMysteryItemMapper;

    @Override
    public PageResult<AdminLotteryPrizeCatalogRowVO> pagePrizes(Integer page, Integer size, Integer pageNum,
                                                                Integer pageSize, String keyword, Integer prizeType,
                                                                Integer catalogStatus, Integer deleteState) {
        Page<LotteryPrize> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<LotteryPrize> w = Wrappers.lambdaQuery(LotteryPrize.class);
        if (deleteState != null) {
            w.eq(LotteryPrize::getDeleteState, deleteState.byteValue());
        } else {
            w.ne(LotteryPrize::getDeleteState, (byte) 1);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(LotteryPrize::getName, keyword.trim());
        }
        if (prizeType != null) {
            w.eq(LotteryPrize::getPrizeType, prizeType.byteValue());
        }
        if (catalogStatus != null) {
            w.eq(LotteryPrize::getCatalogStatus, catalogStatus.byteValue());
        }
        w.orderByDesc(LotteryPrize::getId);
        Page<LotteryPrize> result = lotteryPrizeMapper.selectPage(p, w);
        List<AdminLotteryPrizeCatalogRowVO> rows = result.getRecords().stream().map(this::toRow).toList();
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    public AdminLotteryPrizeCatalogDetailVO detail(Long id) {
        if (id == null || id <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LotteryPrize prize = lotteryPrizeMapper.selectById(id);
        if (prize == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        AdminLotteryPrizeCatalogDetailVO vo = toDetail(prize);
        List<LotteryPrizeMysteryItem> items = lotteryPrizeMysteryItemMapper.selectList(
                Wrappers.lambdaQuery(LotteryPrizeMysteryItem.class)
                        .eq(LotteryPrizeMysteryItem::getPrizeId, id)
                        .eq(LotteryPrizeMysteryItem::getDeleteState, (byte) 0)
                        .orderByAsc(LotteryPrizeMysteryItem::getId));
        for (LotteryPrizeMysteryItem it : items) {
            AdminLotteryPrizeMysteryItemVO v = new AdminLotteryPrizeMysteryItemVO();
            v.setId(it.getId());
            v.setItemType(it.getItemType());
            v.setItemValue(it.getItemValue());
            v.setWeight(it.getWeight());
            vo.getMysteryItems().add(v);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AdminLotteryPrizeCatalogSaveRequest body) {
        validateCatalogSave(body);
        LotteryPrize p = new LotteryPrize();
        p.setName(body.getName().trim());
        p.setPrizeType(body.getPrizeType());
        p.setPrizeValue(body.getPrizeValue() == null ? 0 : body.getPrizeValue());
        p.setStockQuantity(body.getStockQuantity() == null ? -1 : body.getStockQuantity());
        p.setIsMysteryBundle(body.getIsMysteryBundle() == null ? (byte) 0 : body.getIsMysteryBundle());
        p.setImagePath(trim(body.getImagePath()));

        Long prizeId;
        if (body.getId() == null) {
            p.setCatalogStatus(body.getCatalogStatus() == null ? LotteryPrizeCatalogStatus.DRAFT.getCode() : body.getCatalogStatus());
            p.setDeleteState((byte) 0);
            lotteryPrizeMapper.insert(p);
            prizeId = p.getId();
        } else {
            LotteryPrize existing = lotteryPrizeMapper.selectById(body.getId());
            if (existing == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
            }
            p.setId(body.getId());
            p.setDeleteState(existing.getDeleteState());
            p.setCatalogStatus(body.getCatalogStatus() == null ? existing.getCatalogStatus() : body.getCatalogStatus());
            lotteryPrizeMapper.updateById(p);
            prizeId = body.getId();
        }

        if (Objects.equals(p.getPrizeType(), Constant.LOTTERY_PRIZE_GRAND)
                && p.getIsMysteryBundle() != null && p.getIsMysteryBundle() == 1) {
            replaceMysteryItems(prizeId, body.getMysteryItems());
        } else {
            softDeleteAllMysteryItems(prizeId);
        }
        return prizeId;
    }

    @Override
    public void setDeleteState(AdminSetDeleteStateRequest req) {
        if (req.getId() == null || req.getId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (req.getDeleteState() == null || (req.getDeleteState() != 0 && req.getDeleteState() != 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = lotteryPrizeMapper.update(null, new LambdaUpdateWrapper<LotteryPrize>()
                .set(LotteryPrize::getDeleteState, req.getDeleteState().byteValue())
                .eq(LotteryPrize::getId, req.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    public void setCatalogStatus(AdminLotteryPrizeCatalogStatusRequest body) {
        if (body.getId() == null || body.getId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (body.getCatalogStatus() == null || body.getCatalogStatus() < 0 || body.getCatalogStatus() > 2) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = lotteryPrizeMapper.update(null, new LambdaUpdateWrapper<LotteryPrize>()
                .set(LotteryPrize::getCatalogStatus, body.getCatalogStatus().byteValue())
                .eq(LotteryPrize::getId, body.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    public List<AdminLotteryPrizeOptionVO> listOptionsOnShelf() {
        List<LotteryPrize> list = lotteryPrizeMapper.selectPage(new Page<>(1, 500, false),
                Wrappers.lambdaQuery(LotteryPrize.class)
                .eq(LotteryPrize::getDeleteState, (byte) 0)
                .eq(LotteryPrize::getCatalogStatus, LotteryPrizeCatalogStatus.ON_SHELF.getCode())
                .orderByDesc(LotteryPrize::getId)).getRecords();
        return list.stream().map(pr -> {
            AdminLotteryPrizeOptionVO o = new AdminLotteryPrizeOptionVO();
            o.setId(pr.getId());
            o.setName(pr.getName());
            o.setPrizeType(pr.getPrizeType());
            o.setPrizeValue(pr.getPrizeValue());
            o.setIsMysteryBundle(pr.getIsMysteryBundle());
            return o;
        }).toList();
    }

    private void validateCatalogSave(AdminLotteryPrizeCatalogSaveRequest body) {
        if (body == null || !StringUtils.hasText(body.getName())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (body.getPrizeType() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        byte t = body.getPrizeType();
        if (t < 0 || t > 5) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (Objects.equals(t, Constant.LOTTERY_PRIZE_POINTS)) {
            int pv = body.getPrizeValue() == null ? 0 : body.getPrizeValue();
            if (pv <= 0 || pv > Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                        "积分奖须为 1～" + Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX));
            }
        }
        if (Objects.equals(t, Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            int pv = body.getPrizeValue() == null ? 0 : body.getPrizeValue();
            if (pv <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "VIP 奖须填写大于 0 的天数"));
            }
        }
        int stock = body.getStockQuantity() == null ? -1 : body.getStockQuantity();
        if (stock < -1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "库存不能小于 -1"));
        }
        byte bundle = body.getIsMysteryBundle() == null ? (byte) 0 : body.getIsMysteryBundle();
        if (bundle == 1) {
            if (!Objects.equals(t, Constant.LOTTERY_PRIZE_GRAND)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅大奖可配置神秘子项池"));
            }
            if (body.getMysteryItems() == null || body.getMysteryItems().isEmpty()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "神秘大奖须配置至少一条子项"));
            }
            for (AdminLotteryPrizeMysteryItemSaveDTO it : body.getMysteryItems()) {
                if (it.getItemType() == null || (it.getItemType() != Constant.LOTTERY_PRIZE_POINTS
                        && it.getItemType() != Constant.LOTTERY_PRIZE_VIP_DAYS)) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "子项仅支持积分或VIP天"));
                }
                int v = it.getItemValue() == null ? 0 : it.getItemValue();
                if (v <= 0) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "子项数值须为正"));
                }
                if (Objects.equals(it.getItemType(), Constant.LOTTERY_PRIZE_POINTS) && v > Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                            "神秘子项积分不超过 " + Constant.LOTTERY_PRIZE_SINGLE_POINTS_MAX));
                }
                int w = it.getWeight() == null ? 1 : it.getWeight();
                if (w < 0) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
                }
            }
        }
    }

    private void replaceMysteryItems(Long prizeId, List<AdminLotteryPrizeMysteryItemSaveDTO> items) {
        softDeleteAllMysteryItems(prizeId);
        for (AdminLotteryPrizeMysteryItemSaveDTO it : items) {
            LotteryPrizeMysteryItem row = new LotteryPrizeMysteryItem();
            row.setPrizeId(prizeId);
            row.setItemType(it.getItemType());
            row.setItemValue(it.getItemValue());
            row.setWeight(it.getWeight() == null ? 1 : it.getWeight());
            row.setDeleteState((byte) 0);
            lotteryPrizeMysteryItemMapper.insert(row);
        }
    }

    private void softDeleteAllMysteryItems(Long prizeId) {
        lotteryPrizeMysteryItemMapper.update(null, new LambdaUpdateWrapper<LotteryPrizeMysteryItem>()
                .set(LotteryPrizeMysteryItem::getDeleteState, (byte) 1)
                .eq(LotteryPrizeMysteryItem::getPrizeId, prizeId)
                .eq(LotteryPrizeMysteryItem::getDeleteState, (byte) 0));
    }

    private AdminLotteryPrizeCatalogRowVO toRow(LotteryPrize p) {
        AdminLotteryPrizeCatalogRowVO vo = new AdminLotteryPrizeCatalogRowVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setPrizeType(p.getPrizeType());
        vo.setPrizeValue(p.getPrizeValue());
        vo.setStockQuantity(p.getStockQuantity());
        vo.setCatalogStatus(p.getCatalogStatus());
        vo.setIsMysteryBundle(p.getIsMysteryBundle());
        vo.setImagePath(p.getImagePath());
        vo.setDeleteState(p.getDeleteState());
        if (p.getCreateTime() != null) {
            synchronized (DF) {
                vo.setCreateTime(DF.format(p.getCreateTime()));
            }
        } else {
            vo.setCreateTime("");
        }
        if (p.getUpdateTime() != null) {
            synchronized (DF) {
                vo.setUpdateTime(DF.format(p.getUpdateTime()));
            }
        } else {
            vo.setUpdateTime("");
        }
        return vo;
    }

    private AdminLotteryPrizeCatalogDetailVO toDetail(LotteryPrize p) {
        AdminLotteryPrizeCatalogDetailVO vo = new AdminLotteryPrizeCatalogDetailVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setPrizeType(p.getPrizeType());
        vo.setPrizeValue(p.getPrizeValue());
        vo.setStockQuantity(p.getStockQuantity());
        vo.setCatalogStatus(p.getCatalogStatus());
        vo.setIsMysteryBundle(p.getIsMysteryBundle());
        vo.setImagePath(p.getImagePath());
        vo.setDeleteState(p.getDeleteState());
        if (p.getCreateTime() != null) {
            synchronized (DF) {
                vo.setCreateTime(DF.format(p.getCreateTime()));
            }
        } else {
            vo.setCreateTime("");
        }
        if (p.getUpdateTime() != null) {
            synchronized (DF) {
                vo.setUpdateTime(DF.format(p.getUpdateTime()));
            }
        } else {
            vo.setUpdateTime("");
        }
        return vo;
    }

    private String trim(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }
}
