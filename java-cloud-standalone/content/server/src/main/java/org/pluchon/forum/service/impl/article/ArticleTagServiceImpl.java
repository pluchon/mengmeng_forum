package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.ForumArticleTag;
import org.pluchon.forum.entity.db.ForumArticleTagLink;
import org.pluchon.forum.entity.db.ForumArticleTagRequest;
import org.pluchon.forum.entity.dto.AiArticleTagCandidateDTO;
import org.pluchon.forum.entity.dto.AiArticleTagRecommendRequest;
import org.pluchon.forum.entity.dto.AiArticleTagSimilarityRequest;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagRecommendResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagSimilarityResultVO;
import org.pluchon.forum.entity.vo.article.ArticleTagFeedbackVO;
import org.pluchon.forum.entity.vo.article.ArticleTagVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.ForumArticleTagLinkMapper;
import org.pluchon.forum.mapper.ForumArticleTagMapper;
import org.pluchon.forum.mapper.ForumArticleTagRequestMapper;
import org.pluchon.forum.service.interfaces.article.ArticleTagService;
import org.pluchon.forum.cloud.feign.ContentSystemMessageInternalFeignClient;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ArticleTagServiceImpl implements ArticleTagService {

    private static final int MAX_TAGS_PER_ARTICLE = 5;
    private static final int TAG_PAGE_SIZE = 15;
    private static final byte SCOPE_GLOBAL = 0;
    private static final byte SCOPE_CATEGORY = 1;
    private static final byte SCOPE_BOARD = 2;
    private static final byte TAG_NORMAL = 0;
    private static final byte NOT_DELETED = 0;
    private static final String DEFAULT_COLOR_KEY = "mint";
    private static final Set<String> ALLOWED_COLOR_KEYS = Set.of(
            "sky", "rose", "amber", "mint", "violet", "slate", "orange", "teal");

    @Autowired
    private ForumArticleTagMapper tagMapper;
    @Autowired
    private ForumArticleTagLinkMapper linkMapper;
    @Autowired
    private ForumArticleTagRequestMapper requestMapper;
    @Autowired
    private BoardMapper boardMapper;
    @Autowired
    private ContentSystemMessageInternalFeignClient contentSystemMessageInternalFeignClient;
    @Autowired
    private ContentAiGatewayService contentAiGatewayService;

    @Override
    public PageResult<ArticleTagVO> pageForBoard(Long boardId, String keyword, Integer pageNum) {
        int currentPage = PageUtils.getValidPageNum(pageNum);
        List<ArticleTagVO> allTags = searchTags(boardId, keyword);
        long total = allTags.size();
        long pages = total == 0 ? 0 : (total + TAG_PAGE_SIZE - 1) / TAG_PAGE_SIZE;
        long offset = (long) (currentPage - 1) * TAG_PAGE_SIZE;
        int fromIndex = (int) Math.min(offset, total);
        int toIndex = (int) Math.min(offset + TAG_PAGE_SIZE, total);
        List<ArticleTagVO> records = fromIndex >= toIndex
                ? List.of()
                : new ArrayList<>(allTags.subList(fromIndex, toIndex));
        return new PageResult<>(records, total, currentPage, TAG_PAGE_SIZE, pages, currentPage < pages);
    }

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
                .orderByDesc(ForumArticleTag::getCreateTime)
                .orderByDesc(ForumArticleTag::getId));
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
    public List<ArticleTagVO> suggestTags(Long userId, Long boardId, String title, String content, String editorMode) {
        List<ArticleTagVO> pool = listForBoard(boardId);
        if (pool.isEmpty()) {
            return List.of();
        }
        // 与润色 / 封面一致：日配额只算次数，超长正文会让单次调用成本失控
        if (content != null && content.length() > Constant.AI_INPUT_CONTENT_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "正文太长了，请精简后再试"));
        }
        AiArticleTagRecommendRequest request = new AiArticleTagRecommendRequest();
        request.setUserId(userId);
        request.setClientRequestId(java.util.UUID.randomUUID().toString());
        request.setTitle(title == null ? "" : title.trim());
        request.setContent(content == null ? "" : content);
        request.setEditorMode("markdown".equalsIgnoreCase(editorMode) ? "markdown" : "rich");
        request.setCandidates(toAiCandidates(prefilterRecommendCandidates(title, content, pool)));
        AiHubArticleTagRecommendResultVO result = contentAiGatewayService.recommendArticleTags(request);
        List<Long> recommendedIds = result == null || result.getTagIds() == null
                ? List.of() : result.getTagIds();
        if (recommendedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleTagVO> byId = pool.stream().collect(Collectors.toMap(
                ArticleTagVO::getId, item -> item, (left, right) -> left));
        return recommendedIds.stream()
                .distinct()
                .limit(MAX_TAGS_PER_ARTICLE)
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleTagFeedbackVO submitTagFeedback(
            Long userId,
            Long boardId,
            String proposedName,
            String colorKey) {
        String name = normalizeTagName(proposedName);
        if (name == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "标签名需 2～12 字"));
        }
        Board board = requireActiveBoard(boardId);
        Long categoryId = board.getCategoryId() != null ? board.getCategoryId() : 0L;

        if (existsInScope(name, boardId, categoryId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "该标签已存在"));
        }

        String audit = contentAiGatewayService.validateText("论坛帖子标签：" + name);
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

        ArticleTagVO similarTag = findHighlySimilarTag(name, boardId);
        if (similarTag != null) {
            throw new ApplicationException(Result.fail(
                    ResultCode.FAILED_PARAMS_VALIDATE,
                    String.format("已有高度相近标签「%s」，请直接选用", similarTag.getName())));
        }

        ForumArticleTag tag = new ForumArticleTag();
        tag.setName(name);
        tag.setColorKey(normalizeColorKey(colorKey));
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

        contentSystemMessageInternalFeignClient.createMessage(
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

    private List<ArticleTagVO> searchTags(Long boardId, String keyword) {
        List<ArticleTagVO> pool = listForBoard(boardId);
        if (!StringUtils.hasText(keyword)) {
            return pool;
        }
        String normalized = keyword.trim();
        List<ArticleTagVO> lexical = pool.stream()
                .filter(tag -> tag.getName().contains(normalized))
                .sorted(Comparator
                        .comparing((ArticleTagVO tag) -> !tag.getName().equals(normalized))
                        .thenComparingInt(tag -> tag.getName().length()))
                .toList();
        if (!lexical.isEmpty()) {
            return lexical;
        }
        List<Long> semanticIds = contentAiGatewayService.rankSemanticCandidates(
                normalized,
                toSemanticCandidates(pool));
        if (semanticIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleTagVO> byId = pool.stream().collect(Collectors.toMap(
                ArticleTagVO::getId, item -> item, (left, right) -> left));
        return semanticIds.stream()
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .limit(TAG_PAGE_SIZE)
                .toList();
    }

    private List<ArticleTagVO> prefilterRecommendCandidates(
            String title,
            String content,
            List<ArticleTagVO> pool) {
        if (pool.size() <= 80) {
            return pool;
        }
        String query = ((title == null ? "" : title) + " " + (content == null ? "" : content))
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (query.length() > 1200) {
            query = query.substring(0, 1200);
        }
        List<Long> rankedIds = contentAiGatewayService.rankSemanticCandidates(query, toSemanticCandidates(pool));
        if (rankedIds.isEmpty()) {
            return pool.stream().limit(80).toList();
        }
        Map<Long, ArticleTagVO> byId = pool.stream().collect(Collectors.toMap(
                ArticleTagVO::getId, item -> item, (left, right) -> left));
        return rankedIds.stream()
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .limit(80)
                .toList();
    }

    private ArticleTagVO findHighlySimilarTag(String proposedName, Long boardId) {
        List<ArticleTagVO> pool = listForBoard(boardId);
        if (pool.isEmpty()) {
            return null;
        }
        List<Long> rankedIds = contentAiGatewayService.rankSemanticCandidates(
                proposedName,
                toSemanticCandidates(pool));
        if (rankedIds.isEmpty()) {
            return null;
        }
        Map<Long, ArticleTagVO> byId = pool.stream().collect(Collectors.toMap(
                ArticleTagVO::getId, item -> item, (left, right) -> left));
        List<ArticleTagVO> candidates = rankedIds.stream()
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .limit(8)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        AiArticleTagSimilarityRequest request = new AiArticleTagSimilarityRequest();
        request.setProposedName(proposedName);
        request.setCandidates(toAiCandidates(candidates));
        AiHubArticleTagSimilarityResultVO result = contentAiGatewayService.checkArticleTagSimilarity(request);
        return result == null || result.getSimilarTagId() == null
                ? null : byId.get(result.getSimilarTagId());
    }

    private List<AiArticleTagCandidateDTO> toAiCandidates(List<ArticleTagVO> tags) {
        List<AiArticleTagCandidateDTO> result = new ArrayList<>();
        for (ArticleTagVO tag : tags) {
            AiArticleTagCandidateDTO candidate = new AiArticleTagCandidateDTO();
            candidate.setId(tag.getId());
            candidate.setName(tag.getName());
            result.add(candidate);
        }
        return result;
    }

    private List<Map<String, Object>> toSemanticCandidates(List<ArticleTagVO> tags) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ArticleTagVO tag : tags) {
            Map<String, Object> item = new HashMap<>();
            item.put("candidateId", tag.getId());
            item.put("text", tag.getName());
            result.add(item);
        }
        return result;
    }

    private String normalizeColorKey(String colorKey) {
        if (!StringUtils.hasText(colorKey)) {
            return DEFAULT_COLOR_KEY;
        }
        String normalized = colorKey.trim().toLowerCase();
        if (!ALLOWED_COLOR_KEYS.contains(normalized)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "标签颜色不受支持"));
        }
        return normalized;
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
