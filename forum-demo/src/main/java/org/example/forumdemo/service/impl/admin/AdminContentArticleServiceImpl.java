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
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleImage;
import org.example.forumdemo.entity.db.ArticleReply;
import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.db.Category;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.admin.AdminSetArticleStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminArticlePreviewCommentVO;
import org.example.forumdemo.entity.vo.admin.AdminArticlePreviewVO;
import org.example.forumdemo.entity.vo.admin.AdminArticleRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.ArticleImageMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.ArticleReplyMapper;
import org.example.forumdemo.mapper.BoardMapper;
import org.example.forumdemo.mapper.CategoryMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.admin.AdminContentArticleService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminContentArticleServiceImpl implements AdminContentArticleService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private ArticleImageMapper articleImageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private BoardMapper boardMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private ArticleReplyMapper articleReplyMapper;

    @Override
    public PageResult<AdminArticleRowVO> pageArticles(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                      String title, Long boardId, Integer status, Integer state,
                                                      Integer deleteState) {
        Page<Article> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery(Article.class);
        if (deleteState != null) {
            w.eq(Article::getDeleteState, deleteState.byteValue());
        } else {
            w.ne(Article::getDeleteState, (byte) 1);
        }
        if (StringUtils.hasText(title)) {
            w.like(Article::getTitle, title.trim());
        }
        if (boardId != null) {
            w.eq(Article::getBoardId, boardId);
        }
        if (status != null) {
            w.eq(Article::getStatus, status.byteValue());
        }
        if (state != null) {
            w.eq(Article::getState, state.byteValue());
        }
        w.orderByDesc(Article::getCreateTime);
        Page<Article> result = articleMapper.selectPage(p, w);
        List<Article> records = result.getRecords();
        Map<Long, User> users = loadUsers(records.stream().map(Article::getUserId).filter(Objects::nonNull).distinct().toList());
        Map<Long, Board> boards = loadBoards(records.stream().map(Article::getBoardId).filter(Objects::nonNull).distinct().toList());
        List<AdminArticleRowVO> rows = records.stream().map(a -> toRow(a, users, boards)).toList();
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    public void setDeleteState(AdminSetDeleteStateRequest req) {
        validateId(req.getId());
        if (req.getDeleteState() == null || (req.getDeleteState() != 0 && req.getDeleteState() != 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .set(Article::getDeleteState, req.getDeleteState().byteValue())
                .eq(Article::getId, req.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    public void setState(AdminSetArticleStateRequest req) {
        validateId(req.getId());
        if (req.getState() == null || (req.getState() != 0 && req.getState() != 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int n = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .set(Article::getState, req.getState().byteValue())
                .eq(Article::getId, req.getId()));
        if (n == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    public AdminArticlePreviewVO previewArticle(Long id) {
        validateId(id);
        Article a = articleMapper.selectById(id);
        if (a == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        AdminArticlePreviewVO vo = new AdminArticlePreviewVO();
        vo.setId(String.valueOf(a.getId()));
        vo.setTitle(a.getTitle());
        Board b = boardMapper.selectById(a.getBoardId());
        vo.setBoardName(b != null ? b.getName() : "");
        if (b != null && b.getCategoryId() != null) {
            Category cat = categoryMapper.selectById(b.getCategoryId());
            vo.setCategoryName(cat != null ? cat.getName() : "");
        } else {
            vo.setCategoryName("");
        }
        vo.setContentType(a.getContentType() != null ? a.getContentType().intValue() : 0);
        vo.setContent(a.getContent());
        vo.setCoverImg(a.getCoverImg());
        vo.setStatus(a.getStatus() != null ? a.getStatus().intValue() : 0);
        vo.setState(a.getState() != null ? a.getState().intValue() : 0);
        vo.setDeleteState(a.getDeleteState() != null ? a.getDeleteState().intValue() : 0);
        vo.setUserId(String.valueOf(a.getUserId()));
        User u = userMapper.selectById(a.getUserId());
        if (u != null) {
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setAuthorAvatarUrl(u.getAvatarUrl());
            vo.setAuthorVipTier(u.getVipTier() != null ? u.getVipTier().intValue() : 0);
            vo.setAuthorVipExpireAt(u.getVipExpireAt() != null ? DF.format(u.getVipExpireAt()) : null);
        } else {
            vo.setUsername("");
            vo.setNickname("");
            vo.setAuthorAvatarUrl(null);
            vo.setAuthorVipTier(0);
            vo.setAuthorVipExpireAt(null);
        }
        List<ArticleImage> imgs = articleImageMapper.selectList(Wrappers.lambdaQuery(ArticleImage.class)
                .eq(ArticleImage::getArticleId, id)
                .ne(ArticleImage::getDeleteState, (byte) 1)
                .orderByAsc(ArticleImage::getSort));
        vo.setImageUrls(imgs.stream().map(ArticleImage::getImageUrl).filter(Objects::nonNull).toList());
        vo.setTopComments(loadTopComments(id));
        return vo;
    }

    private List<AdminArticlePreviewCommentVO> loadTopComments(Long articleId) {
        List<ArticleReply> replies = articleReplyMapper.selectList(Wrappers.lambdaQuery(ArticleReply.class)
                .eq(ArticleReply::getArticleId, articleId)
                .isNull(ArticleReply::getReplyId)
                .ne(ArticleReply::getDeleteState, (byte) 1)
                .orderByDesc(ArticleReply::getLikeCount)
                .orderByDesc(ArticleReply::getCreateTime)
                .last("LIMIT 10"));
        if (replies.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = replies.stream()
                .map(ArticleReply::getPostUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = loadUsers(userIds);
        List<AdminArticlePreviewCommentVO> list = new ArrayList<>();
        for (ArticleReply r : replies) {
            AdminArticlePreviewCommentVO c = new AdminArticlePreviewCommentVO();
            User ru = users.get(r.getPostUserId());
            c.setNickname(ru != null && ru.getNickname() != null ? ru.getNickname() : "用户");
            c.setAvatarUrl(ru != null ? ru.getAvatarUrl() : null);
            c.setContent(r.getContent() != null ? r.getContent() : "");
            c.setLikeCount(r.getLikeCount() != null ? r.getLikeCount() : 0);
            list.add(c);
        }
        return list;
    }

    private void validateId(Long id) {
        if (id == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Board> loadBoards(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return boardMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Board::getId, Function.identity()));
    }

    private AdminArticleRowVO toRow(Article a, Map<Long, User> users, Map<Long, Board> boards) {
        AdminArticleRowVO vo = new AdminArticleRowVO();
        vo.setId(String.valueOf(a.getId()));
        vo.setTitle(a.getTitle());
        vo.setBoardId(String.valueOf(a.getBoardId()));
        Board b = boards.get(a.getBoardId());
        vo.setBoardName(b != null ? b.getName() : "");
        vo.setUserId(String.valueOf(a.getUserId()));
        User u = users.get(a.getUserId());
        vo.setUsername(u != null ? u.getUsername() : "");
        vo.setNickname(u != null ? u.getNickname() : "");
        vo.setStatus(a.getStatus() != null ? a.getStatus().intValue() : 0);
        vo.setState(a.getState() != null ? a.getState().intValue() : 0);
        vo.setDeleteState(a.getDeleteState() != null ? a.getDeleteState().intValue() : 0);
        vo.setVisitCount(a.getVisitCount());
        vo.setReplyCount(a.getReplyCount());
        vo.setCreateTime(a.getCreateTime() != null ? DF.format(a.getCreateTime()) : "");
        return vo;
    }
}
