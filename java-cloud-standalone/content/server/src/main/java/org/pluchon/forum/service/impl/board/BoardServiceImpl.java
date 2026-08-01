package org.pluchon.forum.service.impl.board;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.converter.BoardConverter;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.db.UserRecommendFeedback;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.board.BoardPublicVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.UserRecommendFeedbackMapper;
import org.pluchon.forum.service.interfaces.board.BoardService;
import org.pluchon.forum.service.impl.remote.ContentFollowLookupService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BoardServiceImpl implements BoardService {

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserRecommendFeedbackMapper userRecommendFeedbackMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ContentFollowLookupService userFollowService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================
    // 板块列表
    // 0 = 升序，1 = 降序；带 5 分钟缓存
    // ============================================================
    @Override
    public List<BoardPublicVO> queryBoardListByOrder(Integer orderBy) {
        if (orderBy == null || orderBy < 0 || orderBy > 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String cacheKey = Constant.REDIS_KEY_BOARD_LIST + orderBy;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return BoardConverter.toPublicVOList(objectMapper.readValue(cached, new TypeReference<List<Board>>() {}));
            } catch (Exception e) {
                log.error("反序列化板块列表缓存失败, orderBy: {}", orderBy, e);
            }
        }
        LambdaQueryWrapper<Board> wrapper = new LambdaQueryWrapper<Board>().ne(Board::getState, 1);
        if (orderBy == 0) {
            wrapper.orderByAsc(Board::getSort);
        } else {
            wrapper.orderByDesc(Board::getSort);
        }
        List<Board> resultList = boardMapper.selectList(wrapper);
        if (resultList == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(resultList),
                    Constant.REDIS_TTL_BOARD_LIST, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("序列化板块列表写入缓存失败, orderBy: {}", orderBy, e);
        }
        return BoardConverter.toPublicVOList(resultList);
    }

    @Override
    public BoardPublicVO queryBoardByBoardId(Long boardId) {
        if (boardId == null || boardId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Board board = boardMapper.selectOne(new LambdaQueryWrapper<Board>()
                .eq(Board::getId, boardId).eq(Board::getDeleteState, (byte) 0));
        if (board == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return BoardConverter.toPublicVO(board);
    }

    @Override
    public Board requireBoardEntity(Long boardId) {
        if (boardId == null || boardId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Board board = boardMapper.selectOne(new LambdaQueryWrapper<Board>()
                .eq(Board::getId, boardId).eq(Board::getDeleteState, (byte) 0));
        if (board == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return board;
    }

    @Override
    public Map<Long, Long> selectBoardNotById() {
        Long boardCount = boardMapper.selectCount(new LambdaQueryWrapper<Board>()
                .eq(Board::getDeleteState, 0).eq(Board::getState, 0));
        Long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, 1)
                .ne(Article::getState, 1)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
        Map<Long, Long> result = new HashMap<>();
        result.put(boardCount, articleCount);
        return result;
    }

    // ============================================================
    // 板块帖子列表（分页）；boardId=0 表示首页全量
    // ============================================================
    @Override
    public PageResult<ArticleListResponse> selectBoardListWithPage(
            Long boardId,
            Integer pageNum,
            Integer pageSize,
            Long loginUserId) {
        if (boardId == null || boardId < 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = PageUtils.getPage(validPageNum, validPageSize);
        List<Long> notInterestedArticleIds = listNotInterestedArticleIds(loginUserId);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, 1)
                .ne(Article::getState, 1)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .orderByDesc(Article::getUpdateTime);
        if (boardId > 0) {
            wrapper.eq(Article::getBoardId, boardId);
        }
        if (!notInterestedArticleIds.isEmpty()) {
            wrapper.notIn(Article::getId, notInterestedArticleIds);
        }
        Page<Article> result = articleMapper.selectPage(page, wrapper);
        List<Article> articleRecords = boardId == 0
                ? mixHomeFeedArticles(result.getRecords(), validPageSize, notInterestedArticleIds)
                : result.getRecords();
        Set<Long> followingIds = (loginUserId != null && loginUserId > 0)
                ? userFollowService.listFollowingIds(loginUserId)
                : Set.of();
        List<ArticleListResponse> records = articleRecords.stream().map(article -> {
            User user = userService.getUserInfoById(article.getUserId());
            ArticleListResponse response = new ArticleListResponse();
            response.setArticle(article);
            response.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(user));
            response.setFromFollowing(followingIds.contains(article.getUserId()));
            return response;
        }).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    /** 首页全站流以新帖为主体，随机插入少量高互动帖子，避免每次只按时间倒序展示。 */
    private List<Article> mixHomeFeedArticles(List<Article> newestArticles, int pageSize,
            List<Long> notInterestedArticleIds) {
        if (newestArticles == null || newestArticles.isEmpty()) {
            return List.of();
        }
        int hotCount = Math.min(Math.min(Math.max(2, pageSize / 5), 4), newestArticles.size());
        LambdaQueryWrapper<Article> engagementWrapper = new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, 1)
                .ne(Article::getState, 1)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .orderByDesc(Article::getLikeCount)
                .orderByDesc(Article::getFavoriteCount)
                .orderByDesc(Article::getReplyCount)
                .orderByDesc(Article::getSubReplyCount)
                .orderByDesc(Article::getVisitCount)
                .orderByDesc(Article::getCreateTime);
        if (!notInterestedArticleIds.isEmpty()) {
            engagementWrapper.notIn(Article::getId, notInterestedArticleIds);
        }
        List<Article> engagementCandidates = articleMapper.selectPage(
                PageUtils.getPage(1, Math.max(pageSize * 3, 30)),
                engagementWrapper)
                .getRecords();
        if (engagementCandidates.isEmpty()) {
            return newestArticles;
        }
        Collections.shuffle(engagementCandidates);
        List<Article> mixed = new ArrayList<>(newestArticles);
        Set<Long> insertedIds = mixed.stream().map(Article::getId).collect(Collectors.toSet());
        int inserted = 0;
        for (Article candidate : engagementCandidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            int existingIndex = findArticleIndex(mixed, candidate.getId());
            if (existingIndex >= 0) {
                mixed.remove(existingIndex);
                int targetIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(mixed.size() + 1);
                mixed.add(targetIndex, candidate);
                inserted++;
                if (inserted >= hotCount) {
                    break;
                }
                continue;
            }
            if (insertedIds.contains(candidate.getId()) || mixed.isEmpty()) {
                continue;
            }
            int replaceIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(mixed.size());
            mixed.set(replaceIndex, candidate);
            insertedIds.add(candidate.getId());
            inserted++;
            if (inserted >= hotCount) {
                break;
            }
        }
        return mixed;
    }

    private int findArticleIndex(List<Article> articles, Long articleId) {
        for (int index = 0; index < articles.size(); index++) {
            if (articleId.equals(articles.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }

    private List<Long> listNotInterestedArticleIds(Long loginUserId) {
        if (loginUserId == null || loginUserId <= 0) {
            return List.of();
        }
        return userRecommendFeedbackMapper.selectList(new LambdaQueryWrapper<UserRecommendFeedback>()
                        .eq(UserRecommendFeedback::getUserId, loginUserId)
                        .eq(UserRecommendFeedback::getDeleteState, 0)
                        .select(UserRecommendFeedback::getArticleId))
                .stream()
                .map(UserRecommendFeedback::getArticleId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ============================================================
    // 帖子计数维护
    // ============================================================
    @Override
    public void addOneById(Long boardId) {
        requireBoardEntity(boardId);
        int result = boardMapper.update(null, new LambdaUpdateWrapper<Board>()
                .eq(Board::getId, boardId).setSql("article_count = article_count + 1"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        invalidateBoardListCache();
    }

    @Override
    public void deleteOneById(Long boardId) {
        requireBoardEntity(boardId);
        int result = boardMapper.update(null, new LambdaUpdateWrapper<Board>()
                .eq(Board::getId, boardId).setSql("article_count = article_count - 1"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        invalidateBoardListCache();
    }

    /** 文章数变更后必须把升降序两份板块列表缓存一起清掉 */
    private void invalidateBoardListCache() {
        stringRedisTemplate.delete(Arrays.asList(Constant.REDIS_KEY_BOARD_LIST + 0, Constant.REDIS_KEY_BOARD_LIST + 1));
    }
}
