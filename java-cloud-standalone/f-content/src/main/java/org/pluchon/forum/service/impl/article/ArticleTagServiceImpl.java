package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.AiAuditUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.ForumArticleTag;
import org.pluchon.forum.entity.db.ForumArticleTagLink;
import org.pluchon.forum.entity.db.ForumArticleTagRequest;
import org.pluchon.forum.entity.vo.article.ArticleTagFeedbackVO;
import org.pluchon.forum.entity.vo.article.ArticleTagVO;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.ForumArticleTagLinkMapper;
import org.pluchon.forum.mapper.ForumArticleTagMapper;
import org.pluchon.forum.mapper.ForumArticleTagRequestMapper;
import org.pluchon.forum.service.interfaces.article.ArticleTagService;
import org.pluchon.forum.service.interfaces.message.SystemMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ArticleTagServiceImpl implements ArticleTagService {

    private static final int MAX_TAGS_PER_ARTICLE = 5;
    private static final byte SCOPE_GLOBAL = 0;
    private static final byte SCOPE_CATEGORY = 1;
    private static final byte SCOPE_BOARD = 2;
    private static final byte TAG_NORMAL = 0;
    private static final byte NOT_DELETED = 0;

    @Resource
    private ForumArticleTagMapper tagMapper;
    @Resource
    private ForumArticleTagLinkMapper linkMapper;
    @Resource
    private ForumArticleTagRequestMapper requestMapper;
    @Resource
    private BoardMapper boardMapper;
    @Resource
    private SystemMessageService systemMessageService;

    @Override
    public List<ArticleTagVO> listForBoard(Long boardId) {
        if (boardId == null || boardId <= 0) {
            return List.of();
        }
        Board board = requireActiveBoard(boardId);
        Long categoryId = board.getCategoryId() != null ? board.getCategoryId() : 0L;
        List<ForumArticleTag> rows = tagMapper.selectList(Wrappers.<ForumArticleTag>lambdaQuery()
                .eq(ForumArticleTag::getDeleteState, NOT_DELETED)
                .eq(ForumArticleTag::getState, TAG_NORMAL)
                .and(w -> w.eq(ForumArticleTag::getScopeType, SCOPE_GLOBAL)
                        .or(x -> x.eq(ForumArticleTag::getScopeType, SCOPE_CATEGORY)
                                .eq(ForumArticleTag::getScopeId, categoryId))
                        .or(x -> x.eq(ForumArticleTag::getScopeType, SCOPE_BOARD)
                                .eq(ForumArticleTag::getScopeId, boardId)))
                .orderByAsc(ForumArticleTag::getSort)
                .orderByAsc(ForumArticleTag::getId));
        return dedupeVo(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindArticleTags(Long articleId, Long boardId, List<Long> tagIds) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        linkMapper.delete(Wrappers.<ForumArticleTagLink>lambdaQuery()
                .eq(ForumArticleTagLink::getArticleId, articleId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<Long> ids = tagIds.stream().filter(Objects::nonNull).distinct().limit(MAX_TAGS_PER_ARTICLE).toList();
        if (ids.isEmpty()) {
            return;
        }
        Set<Long> allowed = listForBoard(boardId).stream().map(ArticleTagVO::getId).collect(Collectors.toSet());
        for (Long tid : ids) {
            if (!allowed.contains(tid)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "标签与版块不匹配"));
            }
            ForumArticleTagLink link = new ForumArticleTagLink();
            link.setArticleId(articleId);
            link.setTagId(tid);
            linkMapper.insert(link);
        }
    }

    @Override
    public List<ArticleTagVO> listByArticleId(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return List.of();
        }
        List<ForumArticleTagLink> links = linkMapper.selectList(Wrappers.<ForumArticleTagLink>lambdaQuery()
                .eq(ForumArticleTagLink::getArticleId, articleId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> ids = links.stream().map(ForumArticleTagLink::getTagId).toList();
        List<ForumArticleTag> tags = tagMapper.selectByIds(ids);
        return dedupeVo(tags);
    }

    @Override
    public List<String> tagNamesByArticleId(Long articleId) {
        return listByArticleId(articleId).stream().map(ArticleTagVO::getName).toList();
    }

    @Override
    public List<ArticleTagVO> suggestTags(Long boardId, String title, String contentSnippet) {
        String snippet = contentSnippet;
        if (snippet != null && snippet.length() > 200) {
            snippet = snippet.substring(0, 200);
        }
        List<ArticleTagVO> pool = listForBoard(boardId);
        if (pool.isEmpty()) {
            return List.of();
        }
        String blob = ((title != null ? title : "") + " " + (contentSnippet != null ? contentSnippet : ""))
                .toLowerCase(Locale.ROOT);
        List<ArticleTagVO> hit = new ArrayList<>();
        for (ArticleTagVO t : pool) {
            String n = t.getName().toLowerCase(Locale.ROOT);
            if (blob.contains(n) || n.length() >= 2 && blob.contains(n.substring(0, 2))) {
                hit.add(t);
            }
        }
        if (hit.size() >= 3) {
            return hit.stream().limit(5).toList();
        }
        for (ArticleTagVO t : pool) {
            if (!hit.contains(t) && hit.size() < 5) {
                hit.add(t);
            }
        }
        return hit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleTagFeedbackVO submitTagFeedback(Long userId, Long boardId, String proposedName) {
        String name = normalizeTagName(proposedName);
        if (name == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "标签名需 2～12 字"));
        }
        Board board = requireActiveBoard(boardId);
        Long categoryId = board.getCategoryId() != null ? board.getCategoryId() : 0L;

        if (existsInScope(name, boardId, categoryId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "该标签已存在"));
        }

        String audit = AiAuditUtils.isTextAllowed("论坛帖子标签：" + name);
        if (audit != null) {
            ForumArticleTagRequest req = new ForumArticleTagRequest();
            req.setUserId(userId);
            req.setBoardId(boardId);
            req.setCategoryId(categoryId);
            req.setProposedName(name);
            req.setStatus((byte) 2);
            req.setAuditMessage(audit);
            req.setDeleteState(NOT_DELETED);
            requestMapper.insert(req);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_CONTENT_ERROR, audit));
        }

        ForumArticleTag tag = new ForumArticleTag();
        tag.setName(name);
        tag.setColorKey(pickColorKey(name));
        tag.setScopeType(SCOPE_BOARD);
        tag.setScopeId(boardId);
        tag.setSort(99);
        tag.setState(TAG_NORMAL);
        tag.setDeleteState(NOT_DELETED);
        tagMapper.insert(tag);

        ForumArticleTagRequest req = new ForumArticleTagRequest();
        req.setUserId(userId);
        req.setBoardId(boardId);
        req.setCategoryId(categoryId);
        req.setProposedName(name);
        req.setStatus((byte) 1);
        req.setAuditMessage("AI 审核通过");
        req.setApprovedTagId(tag.getId());
        req.setDeleteState(NOT_DELETED);
        requestMapper.insert(req);

        systemMessageService.createMessage(
                userId,
                Constant.SYSTEM_MSG_TYPE_TAG_APPROVED,
                Constant.SYSTEM_MSG_TITLE_TAG_APPROVED,
                String.format("您提交的标签「%s」已通过审核，现已可在发帖时选用。", name),
                tag.getId(),
                null);

        return toTagFeedbackVO(tag.getId());
    }

    private static ArticleTagFeedbackVO toTagFeedbackVO(Long tagId) {
        ArticleTagFeedbackVO vo = new ArticleTagFeedbackVO();
        vo.setTagId(tagId);
        vo.setMessage("标签已通过审核，可在列表中选用");
        return vo;
    }

    private Board requireActiveBoard(Long boardId) {
        Board board = boardMapper.selectOne(new LambdaQueryWrapper<Board>()
                .eq(Board::getId, boardId)
                .eq(Board::getDeleteState, (byte) 0));
        if (board == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS,
                    "发布版块不存在，请重新选择版块（若刚迁移数据库请刷新页面）"));
        }
        if (board.getState() != null && board.getState() != 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "该版块已禁用"));
        }
        return board;
    }

    private boolean existsInScope(String name, Long boardId, Long categoryId) {
        Long cnt = tagMapper.selectCount(Wrappers.<ForumArticleTag>lambdaQuery()
                .eq(ForumArticleTag::getName, name)
                .eq(ForumArticleTag::getDeleteState, NOT_DELETED)
                .and(w -> w.and(x -> x.eq(ForumArticleTag::getScopeType, SCOPE_BOARD).eq(ForumArticleTag::getScopeId, boardId))
                        .or(x -> x.eq(ForumArticleTag::getScopeType, SCOPE_CATEGORY).eq(ForumArticleTag::getScopeId, categoryId))
                        .or(x -> x.eq(ForumArticleTag::getScopeType, SCOPE_GLOBAL))));
        return cnt != null && cnt > 0;
    }

    private String normalizeTagName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", "");
        if (s.length() < 2 || s.length() > 12) {
            return null;
        }
        return s;
    }

    private String pickColorKey(String name) {
        if (name.contains("旅") || name.contains("拍")) {
            return "sky";
        }
        if (name.contains("食") || name.contains("餐")) {
            return "amber";
        }
        if (name.contains("宠")) {
            return "rose";
        }
        return "mint";
    }

    private List<ArticleTagVO> dedupeVo(List<ForumArticleTag> rows) {
        Set<String> seen = new LinkedHashSet<>();
        List<ArticleTagVO> out = new ArrayList<>();
        for (ForumArticleTag t : rows) {
            if (t == null || !StringUtils.hasText(t.getName())) {
                continue;
            }
            String key = t.getName().trim();
            if (!seen.add(key)) {
                continue;
            }
            out.add(new ArticleTagVO(t.getId(), key, t.getColorKey() != null ? t.getColorKey() : "sky"));
        }
        return out;
    }
}
