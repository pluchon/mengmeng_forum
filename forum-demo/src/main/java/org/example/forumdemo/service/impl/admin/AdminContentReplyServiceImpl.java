package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AdminPagination;
import org.example.forumdemo.entity.db.ArticleReply;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.admin.AdminSetArticleStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminArticleReplyRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.ArticleReplyMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.admin.AdminContentReplyService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminContentReplyServiceImpl implements AdminContentReplyService {

    private static final int PREVIEW_LEN = 80;
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ArticleReplyMapper articleReplyMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public PageResult<AdminArticleReplyRowVO> pageReplies(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                         Long articleId, String contentKeyword, Integer state,
                                                         Integer deleteState) {
        Page<ArticleReply> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<ArticleReply> w = Wrappers.lambdaQuery(ArticleReply.class)
                .isNull(ArticleReply::getReplyId);
        if (deleteState != null) {
            w.eq(ArticleReply::getDeleteState, deleteState.byteValue());
        } else {
            w.ne(ArticleReply::getDeleteState, (byte) 1);
        }
        if (articleId != null) {
            w.eq(ArticleReply::getArticleId, articleId);
        }
        if (StringUtils.hasText(contentKeyword)) {
            w.like(ArticleReply::getContent, contentKeyword.trim());
        }
        if (state != null) {
            w.eq(ArticleReply::getState, state.byteValue());
        }
        w.orderByDesc(ArticleReply::getCreateTime);
        Page<ArticleReply> result = articleReplyMapper.selectPage(p, w);
        List<ArticleReply> records = result.getRecords();
        List<Long> uids = records.stream().map(ArticleReply::getPostUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> users = uids.isEmpty() ? Map.of()
                : userMapper.selectByIds(uids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<AdminArticleReplyRowVO> rows = records.stream().map(r -> toRow(r, users)).toList();
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    public void setDeleteState(AdminSetDeleteStateRequest req) {
        if (req.getId() == null || req.getDeleteState() == null || (req.getDeleteState() != 0 && req.getDeleteState() != 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = articleReplyMapper.update(null, new LambdaUpdateWrapper<ArticleReply>()
                .set(ArticleReply::getDeleteState, req.getDeleteState().byteValue())
                .eq(ArticleReply::getId, req.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    public void setState(AdminSetArticleStateRequest req) {
        if (req.getId() == null || req.getState() == null || (req.getState() != 0 && req.getState() != 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = articleReplyMapper.update(null, new LambdaUpdateWrapper<ArticleReply>()
                .set(ArticleReply::getState, req.getState().byteValue())
                .eq(ArticleReply::getId, req.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    private AdminArticleReplyRowVO toRow(ArticleReply r, Map<Long, User> users) {
        AdminArticleReplyRowVO vo = new AdminArticleReplyRowVO();
        vo.setId(String.valueOf(r.getId()));
        vo.setArticleId(String.valueOf(r.getArticleId()));
        vo.setPostUserId(String.valueOf(r.getPostUserId()));
        User u = users.get(r.getPostUserId());
        vo.setUsername(u != null ? u.getUsername() : "");
        vo.setNickname(u != null ? u.getNickname() : "");
        String c = r.getContent() != null ? r.getContent() : "";
        vo.setContentPreview(c.length() > PREVIEW_LEN ? c.substring(0, PREVIEW_LEN) + "…" : c);
        vo.setState(r.getState() != null ? r.getState().intValue() : 0);
        vo.setDeleteState(r.getDeleteState() != null ? r.getDeleteState().intValue() : 0);
        vo.setCreateTime(r.getCreateTime() != null ? DF.format(r.getCreateTime()) : "");
        return vo;
    }
}
