package org.example.forumdemo.service.impl.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.RecommendationReasonType;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleFavorite;
import org.example.forumdemo.entity.db.ArticleLike;
import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.db.UserRecommendFeedback;
import org.example.forumdemo.entity.dto.recommendation.NotInterestedArticleRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.recommendation.RecommendArticleVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleFavoriteMapper;
import org.example.forumdemo.mapper.ArticleLikeMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.BoardMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.mapper.UserRecommendFeedbackMapper;
import org.example.forumdemo.service.interfaces.article.ArticleHotRankingService;
import org.example.forumdemo.service.interfaces.recommendation.RecommendationService;
import org.example.forumdemo.service.interfaces.recommendation.UserInterestPreferenceService;
import org.example.forumdemo.service.interfaces.user.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 为你推荐的可解释规则混排实现
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_ENABLED = 0;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleLikeMapper articleLikeMapper;

    @Autowired
    private ArticleFavoriteMapper articleFavoriteMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    private UserRecommendFeedbackMapper feedbackMapper;

    @Autowired
    private UserInterestPreferenceService preferenceService;

    @Autowired
    private UserFollowService userFollowService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    @Override
    public PageResult<RecommendArticleVO> getFeed(Long loginUserId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        int expectedSize = Math.max(validPageSize * (validPageNum + 1), validPageSize);
        int candidateLimit = Math.max(expectedSize * 4, 80);
        Set<Long> feedbackArticleIds = loginUserId == null ? Set.of() : listFeedbackArticleIds(loginUserId);
        Set<Long> explicitBoardIds = loginUserId == null ? Set.of() : preferenceService.listActiveBoardIds(loginUserId);
        boolean personalized = loginUserId != null
                && preferenceService.isPersonalizationEnabled(loginUserId)
                && !explicitBoardIds.isEmpty();

        Set<Long> interactionBoardIds = personalized ? listInteractionBoardIds(loginUserId) : Set.of();
        Set<Long> relatedBoardIds = new LinkedHashSet<>(explicitBoardIds);
        relatedBoardIds.addAll(interactionBoardIds);
        Set<Long> followingIds = loginUserId == null ? Set.of() : userFollowService.listFollowingIds(loginUserId);

        List<Candidate> interestCandidates = personalized
                ? toInterestCandidates(listArticlesByBoards(relatedBoardIds, loginUserId, feedbackArticleIds, candidateLimit), explicitBoardIds)
                : List.of();
        List<Candidate> followingCandidates = personalized
                ? toCandidates(listArticlesByAuthors(followingIds, loginUserId, feedbackArticleIds, candidateLimit), RecommendationReasonType.FOLLOWING)
                : List.of();
        List<Candidate> freshCandidates = toCandidates(listFreshArticles(loginUserId, feedbackArticleIds, candidateLimit),
                personalized ? RecommendationReasonType.FRESH : RecommendationReasonType.COMMUNITY);
        List<Candidate> hotCandidates = toCandidates(listHotArticles(loginUserId, feedbackArticleIds, candidateLimit), RecommendationReasonType.HOT);

        List<Candidate> mixed = personalized
                ? mixPersonalized(interestCandidates, followingCandidates, freshCandidates, hotCandidates, expectedSize, validPageSize)
                : mixPublic(freshCandidates, hotCandidates, expectedSize, validPageSize);
        List<Candidate> visibleCandidates = retainActiveAuthors(mixed);
        int fromIndex = Math.min((validPageNum - 1) * validPageSize, visibleCandidates.size());
        int toIndex = Math.min(fromIndex + validPageSize, visibleCandidates.size());
        List<RecommendArticleVO> records = buildResponse(visibleCandidates.subList(fromIndex, toIndex), followingIds);
        boolean hasNext = visibleCandidates.size() > toIndex;
        return new PageResult<>(records, (long) visibleCandidates.size(), validPageNum, validPageSize,
                (long) Math.max(1, (int) Math.ceil((double) visibleCandidates.size() / validPageSize)), hasNext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNotInterested(Long loginUserId, NotInterestedArticleRequest request) {
        requireUserId(loginUserId);
        if (request == null || request.getArticleId() == null || request.getArticleId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = articleMapper.selectOne(visibleArticleWrapper(loginUserId, Set.of())
                .eq(Article::getId, request.getArticleId()));
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        UserRecommendFeedback existing = feedbackMapper.selectOne(new LambdaQueryWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getUserId, loginUserId)
                .eq(UserRecommendFeedback::getArticleId, request.getArticleId()));
        if (existing == null) {
            UserRecommendFeedback record = new UserRecommendFeedback();
            record.setUserId(loginUserId);
            record.setArticleId(request.getArticleId());
            record.setDeleteState(DELETE_FALSE);
            feedbackMapper.insert(record);
            return;
        }
        feedbackMapper.update(null, new LambdaUpdateWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getId, existing.getId())
                .set(UserRecommendFeedback::getDeleteState, DELETE_FALSE));
    }

    private List<Candidate> mixPersonalized(List<Candidate> interests, List<Candidate> following,
            List<Candidate> fresh, List<Candidate> hot, int expectedSize, int pageSize) {
        List<Candidate> result = new ArrayList<>();
        int interestTarget = (int) Math.ceil(expectedSize * 0.55);
        int followingTarget = (int) Math.ceil(expectedSize * 0.15);
        int freshTarget = (int) Math.ceil(expectedSize * 0.15);
        addCandidates(result, interests, interestTarget, pageSize);
        addCandidates(result, following, followingTarget, pageSize);
        addCandidates(result, fresh, freshTarget, pageSize);
        addCandidates(result, hot, expectedSize, pageSize);
        addCandidates(result, interests, expectedSize, pageSize);
        addCandidates(result, fresh, expectedSize, pageSize);
        return result;
    }

    private List<Candidate> mixPublic(List<Candidate> fresh, List<Candidate> hot, int expectedSize, int pageSize) {
        List<Candidate> result = new ArrayList<>();
        addCandidates(result, fresh, (int) Math.ceil(expectedSize * 0.7), pageSize);
        addCandidates(result, hot, expectedSize, pageSize);
        addCandidates(result, fresh, expectedSize, pageSize);
        return result;
    }

    private void addCandidates(List<Candidate> result, List<Candidate> candidates, int targetSize, int pageSize) {
        for (Candidate candidate : candidates) {
            if (result.size() >= targetSize) {
                return;
            }
            if (containsArticle(result, candidate.article().getId())) {
                continue;
            }
            int currentPage = result.size() / pageSize;
            if (hasAuthorInPage(result, candidate.article().getUserId(), currentPage, pageSize)) {
                continue;
            }
            if (!result.isEmpty() && Objects.equals(result.get(result.size() - 1).article().getBoardId(), candidate.article().getBoardId())) {
                continue;
            }
            result.add(candidate);
        }
    }

    private boolean containsArticle(List<Candidate> candidates, Long articleId) {
        return candidates.stream().anyMatch(item -> Objects.equals(item.article().getId(), articleId));
    }

    private boolean hasAuthorInPage(List<Candidate> candidates, Long authorId, int page, int pageSize) {
        int fromIndex = page * pageSize;
        return candidates.subList(Math.min(fromIndex, candidates.size()), candidates.size()).stream()
                .anyMatch(item -> Objects.equals(item.article().getUserId(), authorId));
    }

    private List<Candidate> retainActiveAuthors(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = candidates.stream().map(item -> item.article().getUserId()).collect(java.util.stream.Collectors.toSet());
        Set<Long> activeAuthorIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, authorIds)
                        .eq(User::getDeleteState, DELETE_FALSE)
                        .eq(User::getState, STATE_ENABLED)
                        .select(User::getId))
                .stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        return candidates.stream().filter(item -> activeAuthorIds.contains(item.article().getUserId())).toList();
    }

    private List<RecommendArticleVO> buildResponse(List<Candidate> candidates, Set<Long> followingIds) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = candidates.stream().map(item -> item.article().getUserId()).collect(java.util.stream.Collectors.toSet());
        Map<Long, User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, authorIds)
                        .eq(User::getDeleteState, DELETE_FALSE)
                        .eq(User::getState, STATE_ENABLED))
                .stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, item -> item));
        Map<Long, String> boardNames = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                        .in(Board::getId, candidates.stream().map(item -> item.article().getBoardId()).distinct().toList())
                        .eq(Board::getDeleteState, DELETE_FALSE)
                        .eq(Board::getState, STATE_ENABLED))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Board::getId, Board::getName));
        List<RecommendArticleVO> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            User author = users.get(candidate.article().getUserId());
            if (author == null) {
                continue;
            }
            RecommendArticleVO response = new RecommendArticleVO();
            response.setArticle(candidate.article());
            response.setUser(new UserBriefVO(author));
            response.setFromFollowing(followingIds.contains(candidate.article().getUserId()));
            response.setRecommendReasonType(candidate.reason().getCode());
            response.setRecommendReason(resolveReason(candidate.reason(), boardNames.get(candidate.article().getBoardId())));
            result.add(response);
        }
        return result;
    }

    private String resolveReason(RecommendationReasonType reason, String boardName) {
        if (reason == RecommendationReasonType.INTEREST && boardName != null) {
            return "因为你选择了「" + boardName + "」";
        }
        return reason.getMessage();
    }

    private List<Candidate> toInterestCandidates(List<Article> articles, Set<Long> explicitBoardIds) {
        List<Candidate> result = new ArrayList<>();
        for (Article article : articles) {
            RecommendationReasonType reason = explicitBoardIds.contains(article.getBoardId())
                    ? RecommendationReasonType.INTEREST
                    : RecommendationReasonType.INTERACTION;
            result.add(new Candidate(article, reason));
        }
        return result;
    }

    private List<Candidate> toCandidates(List<Article> articles, RecommendationReasonType reason) {
        return articles.stream().map(article -> new Candidate(article, reason)).toList();
    }

    private List<Article> listArticlesByBoards(Set<Long> boardIds, Long userId, Set<Long> feedbackArticleIds, int limit) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectPage(PageUtils.getPage(1, limit), visibleArticleWrapper(userId, feedbackArticleIds)
                .in(Article::getBoardId, boardIds)
                .orderByDesc(Article::getUpdateTime)).getRecords();
    }

    private List<Article> listArticlesByAuthors(Set<Long> authorIds, Long userId, Set<Long> feedbackArticleIds, int limit) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectPage(PageUtils.getPage(1, limit), visibleArticleWrapper(userId, feedbackArticleIds)
                .in(Article::getUserId, authorIds)
                .orderByDesc(Article::getUpdateTime)).getRecords();
    }

    private List<Article> listFreshArticles(Long userId, Set<Long> feedbackArticleIds, int limit) {
        return articleMapper.selectPage(PageUtils.getPage(1, limit), visibleArticleWrapper(userId, feedbackArticleIds)
                .orderByDesc(Article::getCreateTime)).getRecords();
    }

    private List<Article> listHotArticles(Long userId, Set<Long> feedbackArticleIds, int limit) {
        List<Long> hotIds = articleHotRankingService.getHotArticleList(limit);
        if (hotIds == null || hotIds.isEmpty()) {
            return List.of();
        }
        List<Article> articles = articleMapper.selectList(visibleArticleWrapper(userId, feedbackArticleIds)
                .in(Article::getId, hotIds));
        Map<Long, Article> articleMap = articles.stream().collect(java.util.stream.Collectors.toMap(Article::getId, item -> item));
        List<Article> result = new ArrayList<>();
        for (Long hotId : hotIds) {
            Article article = articleMap.get(hotId);
            if (article != null) {
                result.add(article);
            }
        }
        return result;
    }

    private LambdaQueryWrapper<Article> visibleArticleWrapper(Long userId, Set<Long> feedbackArticleIds) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getDeleteState, DELETE_FALSE)
                .eq(Article::getState, STATE_ENABLED)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode());
        if (userId != null && userId > 0) {
            wrapper.ne(Article::getUserId, userId);
        }
        if (feedbackArticleIds != null && !feedbackArticleIds.isEmpty()) {
            wrapper.notIn(Article::getId, feedbackArticleIds);
        }
        return wrapper;
    }

    private Set<Long> listInteractionBoardIds(Long userId) {
        Set<Long> articleIds = new HashSet<>();
        articleIds.addAll(articleLikeMapper.selectList(new LambdaQueryWrapper<ArticleLike>()
                        .eq(ArticleLike::getUserId, userId)
                        .select(ArticleLike::getArticleId))
                .stream().map(ArticleLike::getArticleId).toList());
        articleIds.addAll(articleFavoriteMapper.selectList(new LambdaQueryWrapper<ArticleFavorite>()
                        .eq(ArticleFavorite::getUserId, userId)
                        .eq(ArticleFavorite::getDeleteState, DELETE_FALSE)
                        .select(ArticleFavorite::getArticleId))
                .stream().map(ArticleFavorite::getArticleId).toList());
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, articleIds)
                        .eq(Article::getDeleteState, DELETE_FALSE)
                        .eq(Article::getState, STATE_ENABLED)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                        .select(Article::getBoardId))
                .stream()
                .map(Article::getBoardId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> listFeedbackArticleIds(Long userId) {
        return feedbackMapper.selectList(new LambdaQueryWrapper<UserRecommendFeedback>()
                        .eq(UserRecommendFeedback::getUserId, userId)
                        .eq(UserRecommendFeedback::getDeleteState, DELETE_FALSE)
                        .select(UserRecommendFeedback::getArticleId))
                .stream()
                .map(UserRecommendFeedback::getArticleId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
    }

    private record Candidate(Article article, RecommendationReasonType reason) {
    }
}
