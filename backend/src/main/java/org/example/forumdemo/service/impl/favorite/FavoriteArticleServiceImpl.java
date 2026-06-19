package org.example.forumdemo.service.impl.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleFavorite;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.db.UserFavoriteFolder;
import org.example.forumdemo.entity.dto.favorite.MoveFavoriteRequest;
import org.example.forumdemo.entity.dto.favorite.SaveFavoriteRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.favorite.FolderArticleVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleFavoriteMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.UserFavoriteFolderMapper;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.favorite.FavoriteArticleService;
import org.example.forumdemo.service.interfaces.favorite.FavoriteFolderService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FavoriteArticleServiceImpl implements FavoriteArticleService {

    private static final byte DELETE_YES = 1;

    @Autowired
    private ArticleFavoriteMapper favoriteMapper;

    @Autowired
    private UserFavoriteFolderMapper folderMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private FavoriteFolderService folderService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ============================================================
    // 收藏 / 取消 / 移动
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveFavorite(SaveFavoriteRequest req, Long loginUserId) {
        Long articleId = req.getArticleId();
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 帖子必须存在且非禁用 / 删除态
        articleService.selectArticleByArticleId(articleId);
        // 定位目标夹: 留空 → 默认夹(必要时懒加载)
        Long folderId = req.getFolderId();
        if (folderId == null) {
            folderId = folderService.ensureDefaultFolder(loginUserId);
            if (folderId == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED));
            }
        } else {
            folderService.requireFolder(folderId, loginUserId, true);
        }
        // 同一用户对同一帖子只能存在一条未删除收藏; 若软删过同行, 复用并改 folder/恢复
        List<ArticleFavorite> exists = favoriteMapper.selectPage(new Page<>(1, 1, false),
                new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getUserId, loginUserId)
                .eq(ArticleFavorite::getArticleId, articleId)).getRecords();
        ArticleFavorite exist = exists.isEmpty() ? null : exists.get(0);
        if (exist != null && (exist.getDeleteState() == null || exist.getDeleteState() != DELETE_YES)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FAVORITE_ALREADY_EXISTS));
        }
        // 并发安全: 真正的"新增/复活"必须 +1 计数; 已被别人抢先复活的请求, 直接抛已收藏避免重复加分.
        boolean reallyAdded;
        try {
            if (exist == null) {
                ArticleFavorite row = new ArticleFavorite();
                row.setUserId(loginUserId);
                row.setFolderId(folderId);
                row.setArticleId(articleId);
                favoriteMapper.insert(row);
                reallyAdded = true;
            } else {
                // 复活旧软删记录: 仅在仍为 delete=1 时才更新; 抢到了才记为真的"新增"
                int updated = favoriteMapper.update(null, new LambdaUpdateWrapper<ArticleFavorite>()
                        .eq(ArticleFavorite::getId, exist.getId())
                        .eq(ArticleFavorite::getDeleteState, DELETE_YES)
                        .set(ArticleFavorite::getFolderId, folderId)
                        .set(ArticleFavorite::getDeleteState, (byte) 0));
                if (updated <= 0) {
                    // 已被并发请求复活了 -> 当前请求按"已收藏"语义返回, 不重复加计数
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_FAVORITE_ALREADY_EXISTS));
                }
                reallyAdded = true;
            }
        } catch (DuplicateKeyException dup) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FAVORITE_ALREADY_EXISTS));
        }
        if (reallyAdded) {
            folderMapper.incrementItemCount(folderId);
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId).setSql("favorite_count = favorite_count + 1"));
            stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                    String.valueOf(articleId), Constant.HOT_SCORE_WEIGHT_FAVORITE);
        }
        log.info("用户 {} 收藏帖子 {} 到夹 {}", loginUserId, articleId, folderId);
        return folderId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelFavorite(Long articleId, Long loginUserId) {
        if (articleId == null || articleId <= 0 || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ArticleFavorite row = favoriteMapper.selectOne(new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getUserId, loginUserId)
                .eq(ArticleFavorite::getArticleId, articleId)
                .ne(ArticleFavorite::getDeleteState, DELETE_YES));
        if (row == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FAVORITE_NOT_EXISTS));
        }
        int updated = favoriteMapper.update(null, new LambdaUpdateWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getId, row.getId())
                .ne(ArticleFavorite::getDeleteState, DELETE_YES)
                .set(ArticleFavorite::getDeleteState, DELETE_YES));
        if (updated <= 0) {
            // 并发取消; 不报错保持幂等, 直接返回成功
            return;
        }
        folderMapper.decrementItemCount(row.getFolderId());
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .setSql("favorite_count = GREATEST(favorite_count - 1, 0)"));
        stringRedisTemplate.opsForZSet().incrementScore(Constant.REDIS_KEY_HOT_ARTICLES,
                String.valueOf(articleId), -Constant.HOT_SCORE_WEIGHT_FAVORITE);
        log.info("用户 {} 取消收藏帖子 {}", loginUserId, articleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveFavorite(MoveFavoriteRequest req, Long loginUserId) {
        Long articleId = req.getArticleId();
        Long toFolderId = req.getToFolderId();
        if (articleId == null || toFolderId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        folderService.requireFolder(toFolderId, loginUserId, true);
        ArticleFavorite row = favoriteMapper.selectOne(new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getUserId, loginUserId)
                .eq(ArticleFavorite::getArticleId, articleId)
                .ne(ArticleFavorite::getDeleteState, DELETE_YES));
        if (row == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FAVORITE_NOT_EXISTS));
        }
        if (Objects.equals(row.getFolderId(), toFolderId)) {
            return;
        }
        Long fromFolderId = row.getFolderId();
        favoriteMapper.update(null, new LambdaUpdateWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getId, row.getId())
                .set(ArticleFavorite::getFolderId, toFolderId));
        folderMapper.decrementItemCount(fromFolderId);
        folderMapper.incrementItemCount(toFolderId);
        log.info("用户 {} 把帖子 {} 从夹 {} 移到夹 {}", loginUserId, articleId, fromFolderId, toFolderId);
    }

    // ============================================================
    // 查询
    // ============================================================
    @Override
    public boolean isFavorited(Long articleId, Long loginUserId) {
        if (articleId == null || loginUserId == null || loginUserId <= 0) {
            return false;
        }
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getUserId, loginUserId)
                .eq(ArticleFavorite::getArticleId, articleId)
                .ne(ArticleFavorite::getDeleteState, DELETE_YES));
        return count != null && count > 0;
    }

    @Override
    public PageResult<FolderArticleVO> queryFolderArticles(Long folderId, Long loginUserId,
                                                           Integer pageNum, Integer pageSize) {
        UserFavoriteFolder folder = folderService.requireFolder(folderId, loginUserId, false);
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);
        Page<ArticleFavorite> page = PageUtils.getPage(p, s);
        Page<ArticleFavorite> result = favoriteMapper.selectPage(page, new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getFolderId, folder.getId())
                .ne(ArticleFavorite::getDeleteState, DELETE_YES)
                .orderByDesc(ArticleFavorite::getCreateTime));
        List<FolderArticleVO> records = new ArrayList<>(result.getRecords().size());
        // 批量加载 article + author, 避免 N+1; 详情失败的帖子被跳过(可能被作者删了 / 禁言)
        List<Long> articleIds = result.getRecords().stream().map(ArticleFavorite::getArticleId).collect(Collectors.toList());
        Map<Long, Article> articleMap = batchLoadArticles(articleIds);
        Map<Long, UserBriefVO> userMap = batchLoadAuthors(articleMap.values());
        for (ArticleFavorite fav : result.getRecords()) {
            Article article = articleMap.get(fav.getArticleId());
            if (article == null) continue;
            UserBriefVO author = userMap.get(article.getUserId());
            records.add(new FolderArticleVO(article, author, fav.getCreateTime()));
        }
        return new PageResult<>(records, result.getTotal(), p, s, result.getPages(), result.hasNext());
    }

    // ============ 内部 ============
    private Map<Long, Article> batchLoadArticles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new HashMap<>();
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids).ne(Article::getDeleteState, DELETE_YES)
                .ne(Article::getState, (byte) 1));
        Map<Long, Article> map = new HashMap<>(rows.size() * 2);
        for (Article a : rows) map.put(a.getId(), a);
        return map;
    }

    private Map<Long, UserBriefVO> batchLoadAuthors(Collection<Article> articles) {
        Map<Long, UserBriefVO> map = new HashMap<>();
        for (Article a : articles) {
            Long uid = a.getUserId();
            if (map.containsKey(uid)) continue;
            try {
                User u = userService.getUserInfoById(uid);
                if (u != null) map.put(uid, new UserBriefVO(u));
            } catch (ApplicationException e) {
                log.warn("收藏夹列表中作者 {} 已不可用, 跳过", uid);
            }
        }
        return map;
    }
}
