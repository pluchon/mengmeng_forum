package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AdminPagination;
import org.example.forumdemo.entity.db.Category;
import org.example.forumdemo.entity.db.ForumNotice;
import org.example.forumdemo.entity.dto.admin.AdminForumNoticeSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminForumNoticeUpdateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetNoticePinTopRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetNoticePublishStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminForumNoticeDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminForumNoticeRowVO;
import org.example.forumdemo.entity.vo.admin.AdminIdNameVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.CategoryMapper;
import org.example.forumdemo.mapper.ForumNoticeMapper;
import org.example.forumdemo.service.interfaces.admin.AdminForumNoticeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

@Service
public class AdminForumNoticeServiceImpl implements AdminForumNoticeService {

    /** 管理端列表/详情时间：固定按东八区墙钟展示 */
    private static final ThreadLocal<SimpleDateFormat> CN_TS = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return f;
    });

    private static String formatCn(Date d) {
        if (d == null) {
            return "";
        }
        return CN_TS.get().format(d);
    }

    private static final byte KIND_BOARD_RULE = 4;

    @Resource
    private ForumNoticeMapper forumNoticeMapper;

    @Resource
    private CategoryMapper categoryMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResult<AdminForumNoticeRowVO> pageNotices(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                         Integer noticeKind, Long categoryScope, String title,
                                                         Integer deleteState, String sortBy, String sortOrder) {
        Page<ForumNotice> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<ForumNotice> w = Wrappers.lambdaQuery(ForumNotice.class);
        if (deleteState != null) {
            w.eq(ForumNotice::getDeleteState, deleteState.byteValue());
        }
        if (noticeKind != null) {
            w.eq(ForumNotice::getNoticeKind, noticeKind.byteValue());
        }
        if (categoryScope != null) {
            w.eq(ForumNotice::getCategoryScope, categoryScope);
        }
        if (StringUtils.hasText(title)) {
            w.like(ForumNotice::getTitle, title.trim());
        }
        applyListOrder(w, sortBy, sortOrder);
        Page<ForumNotice> result = forumNoticeMapper.selectPage(p, w);
        List<AdminForumNoticeRowVO> rows = result.getRecords().stream().map(this::toRow).collect(Collectors.toList());
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    public AdminForumNoticeDetailVO getDetail(Long id) {
        ForumNotice n = forumNoticeMapper.selectById(id);
        if (n == null) {
            return null;
        }
        return toDetail(n);
    }

    @Override
    public List<AdminIdNameVO> listCategoryOptions() {
        return categoryMapper.selectList(Wrappers.lambdaQuery(Category.class)
                        .eq(Category::getDeleteState, (byte) 0)
                        .orderByAsc(Category::getSort))
                .stream()
                .map(c -> new AdminIdNameVO(String.valueOf(c.getId()), c.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public void save(AdminForumNoticeSaveRequest body) {
        prepareScope(body);
        normalizeBodyJson(body);
        validateBodyJson(body.getBodyJson());
        validateCategoryScope(body.getNoticeKind(), body.getCategoryScope());
        assertUniqueKeys(body.getNoticeKind().byteValue(), body.getCategoryScope(), body.getSidebarKey(), null);
        ForumNotice n = new ForumNotice();
        fillFromRequest(n, body);
        n.setSort(0);
        n.setDeleteState((byte) 0);
        forumNoticeMapper.insert(n);
    }

    @Override
    public void update(AdminForumNoticeUpdateRequest body) {
        ForumNotice existing = forumNoticeMapper.selectById(body.getId());
        if (existing == null || (existing.getDeleteState() != null && existing.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        prepareScope(body);
        normalizeBodyJson(body);
        validateBodyJson(body.getBodyJson());
        validateCategoryScope(body.getNoticeKind(), body.getCategoryScope());
        assertUniqueKeys(body.getNoticeKind().byteValue(), body.getCategoryScope(), body.getSidebarKey(), body.getId());
        fillFromRequest(existing, body);
        forumNoticeMapper.updateById(existing);
    }

    @Override
    public void setDeleteState(AdminSetDeleteStateRequest body) {
        forumNoticeMapper.update(null, new LambdaUpdateWrapper<ForumNotice>()
                .set(ForumNotice::getDeleteState, body.getDeleteState().byteValue())
                .eq(ForumNotice::getId, body.getId()));
    }

    @Override
    public void setPublishState(AdminSetNoticePublishStateRequest body) {
        forumNoticeMapper.update(null, new LambdaUpdateWrapper<ForumNotice>()
                .set(ForumNotice::getPublishState, body.getPublishState())
                .eq(ForumNotice::getId, body.getId()));
    }

    @Override
    public void setPinTop(AdminSetNoticePinTopRequest body) {
        ForumNotice n = forumNoticeMapper.selectById(body.getId());
        if (n == null || (n.getDeleteState() != null && n.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        forumNoticeMapper.update(null, new LambdaUpdateWrapper<ForumNotice>()
                .set(ForumNotice::getPinTop, body.getPinTop())
                .eq(ForumNotice::getId, body.getId()));
    }

    /** 管理端列表排序：默认 id 升序；可选 updateTime 升序/降序 */
    private void applyListOrder(LambdaQueryWrapper<ForumNotice> w, String sortBy, String sortOrder) {
        boolean desc = "desc".equalsIgnoreCase(StringUtils.hasText(sortOrder) ? sortOrder.trim() : "");
        if ("updateTime".equalsIgnoreCase(StringUtils.hasText(sortBy) ? sortBy.trim() : "")) {
            if (desc) {
                w.orderByDesc(ForumNotice::getUpdateTime).orderByDesc(ForumNotice::getId);
            } else {
                w.orderByAsc(ForumNotice::getUpdateTime).orderByAsc(ForumNotice::getId);
            }
            return;
        }
        w.orderByAsc(ForumNotice::getId);
    }

    private void prepareScope(AdminForumNoticeSaveRequest body) {
        if (body.getNoticeKind() == null || !Byte.valueOf(KIND_BOARD_RULE).equals(body.getNoticeKind())) {
            body.setCategoryScope(0L);
        }
    }

    private void normalizeBodyJson(AdminForumNoticeSaveRequest body) {
        if (!StringUtils.hasText(body.getBodyJson())) {
            body.setBodyJson("{}");
        } else {
            body.setBodyJson(body.getBodyJson().trim());
        }
    }

    private void fillFromRequest(ForumNotice n, AdminForumNoticeSaveRequest body) {
        n.setNoticeKind(body.getNoticeKind());
        n.setCategoryScope(body.getCategoryScope());
        n.setTemplateId(body.getTemplateId().trim());
        n.setSidebarKey(body.getSidebarKey().trim());
        n.setTitle(body.getTitle().trim());
        n.setSubtitle(StringUtils.hasText(body.getSubtitle()) ? body.getSubtitle().trim() : "");
        n.setContentMarkdown(body.getContentMarkdown().trim());
        n.setBodyJson(body.getBodyJson());
        n.setPinTop(body.getPinTop());
        n.setPublishState(body.getPublishState());
    }

    private void validateBodyJson(String raw) {
        try {
            objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "body_json 不是合法 JSON"));
        }
    }

    private void validateCategoryScope(Byte noticeKind, Long categoryScope) {
        if (noticeKind == null || noticeKind < 0 || noticeKind > 4) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "notice_kind 须在 0~4"));
        }
        if (!Byte.valueOf(KIND_BOARD_RULE).equals(noticeKind)) {
            if (categoryScope == null || categoryScope != 0L) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "非版规公告 category_scope 必须为 0"));
            }
            return;
        }
        if (categoryScope == null || categoryScope < 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "版规公告 category_scope 非法"));
        }
        if (categoryScope == 0L) {
            return;
        }
        Category c = categoryMapper.selectById(categoryScope);
        if (c == null || (c.getDeleteState() != null && c.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "category_scope 对应的分类不存在"));
        }
    }

    private void assertUniqueKeys(byte noticeKind, long categoryScope, String sidebarKey, Long excludeId) {
        LambdaQueryWrapper<ForumNotice> w = Wrappers.lambdaQuery(ForumNotice.class)
                .eq(ForumNotice::getNoticeKind, noticeKind)
                .eq(ForumNotice::getCategoryScope, categoryScope)
                .eq(ForumNotice::getSidebarKey, sidebarKey)
                .ne(ForumNotice::getDeleteState, (byte) 1);
        if (excludeId != null) {
            w.ne(ForumNotice::getId, excludeId);
        }
        Long cnt = forumNoticeMapper.selectCount(w);
        if (cnt != null && cnt > 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "同类型同分类范围下 sidebar_key 已存在"));
        }
    }

    private AdminForumNoticeRowVO toRow(ForumNotice n) {
        AdminForumNoticeRowVO vo = new AdminForumNoticeRowVO();
        vo.setId(String.valueOf(n.getId()));
        vo.setNoticeKind(n.getNoticeKind() != null ? n.getNoticeKind().intValue() : null);
        vo.setCategoryScope(n.getCategoryScope() != null ? String.valueOf(n.getCategoryScope()) : "0");
        vo.setTemplateId(n.getTemplateId());
        vo.setSidebarKey(n.getSidebarKey());
        vo.setTitle(n.getTitle());
        vo.setSubtitle(n.getSubtitle());
        String md = n.getContentMarkdown();
        if (md != null && md.length() > 120) {
            vo.setContentPreview(md.substring(0, 120).replace('\n', ' ') + "…");
        } else {
            vo.setContentPreview(md != null ? md.replace('\n', ' ') : "");
        }
        String bj = n.getBodyJson();
        if (bj != null && bj.length() > 120) {
            vo.setBodyPreview(bj.substring(0, 120) + "…");
        } else {
            vo.setBodyPreview(bj);
        }
        vo.setPinTop(n.getPinTop() != null ? n.getPinTop().intValue() : 0);
        vo.setSort(n.getSort());
        vo.setPublishState(n.getPublishState() != null ? n.getPublishState().intValue() : 0);
        vo.setDeleteState(n.getDeleteState() != null ? n.getDeleteState().intValue() : 0);
        vo.setCreateTime(formatCn(n.getCreateTime()));
        vo.setUpdateTime(formatCn(n.getUpdateTime()));
        return vo;
    }

    private AdminForumNoticeDetailVO toDetail(ForumNotice n) {
        AdminForumNoticeDetailVO vo = new AdminForumNoticeDetailVO();
        vo.setId(String.valueOf(n.getId()));
        vo.setNoticeKind(n.getNoticeKind() != null ? n.getNoticeKind().intValue() : null);
        vo.setCategoryScope(n.getCategoryScope() != null ? String.valueOf(n.getCategoryScope()) : "0");
        vo.setTemplateId(n.getTemplateId());
        vo.setSidebarKey(n.getSidebarKey());
        vo.setTitle(n.getTitle());
        vo.setSubtitle(n.getSubtitle());
        vo.setContentMarkdown(n.getContentMarkdown());
        vo.setBodyJson(n.getBodyJson());
        vo.setPinTop(n.getPinTop() != null ? n.getPinTop().intValue() : 0);
        vo.setSort(n.getSort());
        vo.setPublishState(n.getPublishState() != null ? n.getPublishState().intValue() : 0);
        vo.setDeleteState(n.getDeleteState() != null ? n.getDeleteState().intValue() : 0);
        vo.setCreateTime(formatCn(n.getCreateTime()));
        vo.setUpdateTime(formatCn(n.getUpdateTime()));
        return vo;
    }
}
