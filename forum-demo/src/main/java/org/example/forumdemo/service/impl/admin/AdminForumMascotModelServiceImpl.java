package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.util.AdminPagination;
import org.example.forumdemo.entity.db.ForumMascotModel;
import org.example.forumdemo.entity.dto.admin.AdminForumMascotModelSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminForumMascotShelfRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminForumMascotModelRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.ForumMascotModelMapper;
import org.example.forumdemo.service.interfaces.admin.AdminForumMascotModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;

@Service
public class AdminForumMascotModelServiceImpl implements AdminForumMascotModelService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ForumMascotModelMapper forumMascotModelMapper;

    @Override
    public PageResult<AdminForumMascotModelRowVO> pageModels(Integer page, Integer size, Integer pageNum,
                                                             Integer pageSize, String keyword, Integer shelfStatus,
                                                             Integer deleteState) {
        Page<ForumMascotModel> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<ForumMascotModel> w = Wrappers.lambdaQuery(ForumMascotModel.class);
        if (deleteState != null) {
            w.eq(ForumMascotModel::getDeleteState, deleteState.byteValue());
        } else {
            w.ne(ForumMascotModel::getDeleteState, (byte) 1);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.like(ForumMascotModel::getName, k);
        }
        if (shelfStatus != null) {
            w.eq(ForumMascotModel::getShelfStatus, shelfStatus.byteValue());
        }
        w.orderByAsc(ForumMascotModel::getSortOrder).orderByDesc(ForumMascotModel::getId);
        Page<ForumMascotModel> result = forumMascotModelMapper.selectPage(p, w);
        List<AdminForumMascotModelRowVO> rows = result.getRecords().stream().map(this::toRow).toList();
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AdminForumMascotModelSaveRequest body) {
        if (body == null || !StringUtils.hasText(body.getCode()) || !StringUtils.hasText(body.getName())
                || !StringUtils.hasText(body.getModelRelPath())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String code = body.getCode().trim();
        String path = body.getModelRelPath().trim().replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        BigDecimal scale = body.getModelScale() != null ? body.getModelScale() : new BigDecimal("0.1000");
        int px = body.getPosX() != null ? body.getPosX() : 0;
        int py = body.getPosY() != null ? body.getPosY() : 72;
        int sw = body.getStageWidth() != null ? body.getStageWidth() : 260;
        int sh = body.getStageHeight() != null ? body.getStageHeight() : 320;
        byte shelf = body.getShelfStatus() == null ? (byte) 0 : body.getShelfStatus().byteValue();
        int sort = body.getSortOrder() != null ? body.getSortOrder() : 0;

        ForumMascotModel row = new ForumMascotModel();
        row.setCode(code);
        row.setName(body.getName().trim());
        row.setModelRelPath(path);
        row.setModelScale(scale);
        row.setPosX(px);
        row.setPosY(py);
        row.setStageWidth(sw);
        row.setStageHeight(sh);
        row.setShelfStatus(shelf);
        row.setSortOrder(sort);

        if (body.getId() == null) {
            Long dup = forumMascotModelMapper.selectCount(Wrappers.lambdaQuery(ForumMascotModel.class)
                    .eq(ForumMascotModel::getCode, code).ne(ForumMascotModel::getDeleteState, (byte) 1));
            if (dup != null && dup > 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "code 已存在"));
            }
            row.setDeleteState((byte) 0);
            forumMascotModelMapper.insert(row);
            return row.getId();
        }
        ForumMascotModel existing = forumMascotModelMapper.selectById(body.getId());
        if (existing == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        Long dup = forumMascotModelMapper.selectCount(Wrappers.lambdaQuery(ForumMascotModel.class)
                .eq(ForumMascotModel::getCode, code)
                .ne(ForumMascotModel::getId, body.getId())
                .ne(ForumMascotModel::getDeleteState, (byte) 1));
        if (dup != null && dup > 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "code 已存在"));
        }
        row.setId(body.getId());
        row.setDeleteState(existing.getDeleteState());
        forumMascotModelMapper.updateById(row);
        return body.getId();
    }

    @Override
    public void setShelfStatus(AdminForumMascotShelfRequest body) {
        if (body.getId() == null || body.getShelfStatus() == null
                || body.getShelfStatus() < 0 || body.getShelfStatus() > 2) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = forumMascotModelMapper.update(null, new LambdaUpdateWrapper<ForumMascotModel>()
                .set(ForumMascotModel::getShelfStatus, body.getShelfStatus().byteValue())
                .eq(ForumMascotModel::getId, body.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    public void setDeleteState(AdminSetDeleteStateRequest req) {
        if (req.getId() == null || req.getId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (req.getDeleteState() == null || (req.getDeleteState() != 0 && req.getDeleteState() != 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = forumMascotModelMapper.update(null, new LambdaUpdateWrapper<ForumMascotModel>()
                .set(ForumMascotModel::getDeleteState, req.getDeleteState().byteValue())
                .eq(ForumMascotModel::getId, req.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    private AdminForumMascotModelRowVO toRow(ForumMascotModel m) {
        AdminForumMascotModelRowVO vo = new AdminForumMascotModelRowVO();
        vo.setId(m.getId());
        vo.setCode(m.getCode());
        vo.setName(m.getName());
        vo.setModelRelPath(m.getModelRelPath());
        vo.setModelScale(m.getModelScale());
        vo.setPosX(m.getPosX());
        vo.setPosY(m.getPosY());
        vo.setStageWidth(m.getStageWidth());
        vo.setStageHeight(m.getStageHeight());
        vo.setShelfStatus(m.getShelfStatus() == null ? null : m.getShelfStatus().intValue());
        vo.setSortOrder(m.getSortOrder());
        vo.setDeleteState(m.getDeleteState() == null ? null : m.getDeleteState().intValue());
        synchronized (DF) {
            vo.setCreateTime(m.getCreateTime() != null ? DF.format(m.getCreateTime()) : "");
            vo.setUpdateTime(m.getUpdateTime() != null ? DF.format(m.getUpdateTime()) : "");
        }
        return vo;
    }
}
