package org.example.forumdemo.service.impl.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.ArticleFavorite;
import org.example.forumdemo.entity.db.UserFavoriteFolder;
import org.example.forumdemo.entity.dto.favorite.CreateFolderRequest;
import org.example.forumdemo.entity.dto.favorite.UpdateFolderRequest;
import org.example.forumdemo.entity.vo.favorite.FolderVO;
import org.example.forumdemo.mapper.ArticleFavoriteMapper;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.UserFavoriteFolderMapper;
import org.example.forumdemo.service.interfaces.article.ArticleHotRankingService;
import org.example.forumdemo.service.interfaces.favorite.FavoriteFolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FavoriteFolderServiceImpl implements FavoriteFolderService {

    private static final byte PUBLIC_YES = 1;
    private static final byte PUBLIC_NO  = 0;
    private static final byte DEFAULT_YES = 1;
    private static final byte DEFAULT_NO  = 0;
    private static final byte DELETE_YES = 1;
    private static final int FOLDER_VISIBILITY_CHANGE_LIMIT = 8;
    private static final long FOLDER_VISIBILITY_WINDOW_MS = 10 * 60 * 1000L;
    private static final String REDIS_FOLDER_VISIBILITY_CHANGES = "forum:folder:visibility:changes:";

    @Autowired
    private UserFavoriteFolderMapper folderMapper;

    @Autowired
    private ArticleFavoriteMapper favoriteMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    // ============================================================
    // 创建 / 修改 / 删除
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFolder(CreateFolderRequest req, Long loginUserId) {
        if (loginUserId == null || loginUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        String name = req.getName() == null ? "" : req.getName().trim();
        if (!StringUtils.hasLength(name)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 同名校验: 用户视角下名称唯一, 避免重复夹混淆
        Long dup = folderMapper.selectCount(new LambdaQueryWrapper<UserFavoriteFolder>()
                .eq(UserFavoriteFolder::getUserId, loginUserId)
                .eq(UserFavoriteFolder::getName, name)
                .ne(UserFavoriteFolder::getDeleteState, DELETE_YES));
        if (dup != null && dup > 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FOLDER_NAME_DUPLICATE));
        }
        UserFavoriteFolder folder = new UserFavoriteFolder();
        folder.setUserId(loginUserId);
        folder.setName(name);
        folder.setIsPublic(req.getIsPublic() == null ? PUBLIC_YES : req.getIsPublic());
        folder.setIsDefault(DEFAULT_NO);
        folder.setSortOrder(0);
        folder.setItemCount(0);
        folderMapper.insert(folder);
        return folder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFolder(UpdateFolderRequest req, Long loginUserId) {
        UserFavoriteFolder folder = requireFolder(req.getFolderId(), loginUserId, true);
        // 同名校验(仅当用户改了名字才做)
        if (StringUtils.hasLength(req.getName()) && !Objects.equals(req.getName().trim(), folder.getName())) {
            String newName = req.getName().trim();
            Long dup = folderMapper.selectCount(new LambdaQueryWrapper<UserFavoriteFolder>()
                    .eq(UserFavoriteFolder::getUserId, loginUserId)
                    .eq(UserFavoriteFolder::getName, newName)
                    .ne(UserFavoriteFolder::getId, folder.getId())
                    .ne(UserFavoriteFolder::getDeleteState, DELETE_YES));
            if (dup != null && dup > 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_FOLDER_NAME_DUPLICATE));
            }
            folder.setName(newName);
        }
        if (req.getIsPublic() != null && !Objects.equals(req.getIsPublic(), folder.getIsPublic())) {
            assertFolderVisibilityChangeAllowed(loginUserId);
            folder.setIsPublic(req.getIsPublic());
        }
        if (req.getSortOrder() != null) {
            folder.setSortOrder(req.getSortOrder());
        }
        if (folderMapper.updateById(folder) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long folderId, Long loginUserId) {
        UserFavoriteFolder folder = requireFolder(folderId, loginUserId, true);
        if (folder.getIsDefault() != null && folder.getIsDefault() == DEFAULT_YES) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_DEFAULT_FOLDER_CANNOT_DELETE));
        }
        // 1) 列出夹内所有未删除的收藏记录, 用于回扣 article.favorite_count 与热帖榜分
        List<ArticleFavorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getFolderId, folderId)
                .ne(ArticleFavorite::getDeleteState, DELETE_YES));
        // 2) 软删夹内所有 article_favorite
        if (!favorites.isEmpty()) {
            favoriteMapper.update(null, new LambdaUpdateWrapper<ArticleFavorite>()
                    .eq(ArticleFavorite::getFolderId, folderId)
                    .ne(ArticleFavorite::getDeleteState, DELETE_YES)
                    .set(ArticleFavorite::getDeleteState, DELETE_YES));
        }
        // 3) 软删夹本身
        if (folderMapper.update(null, new LambdaUpdateWrapper<UserFavoriteFolder>()
                .eq(UserFavoriteFolder::getId, folderId)
                .ne(UserFavoriteFolder::getDeleteState, DELETE_YES)
                .set(UserFavoriteFolder::getDeleteState, DELETE_YES)) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        // 4) 事务提交后再回扣帖子计数 / 热帖榜分; 这里直接 SQL 内 -1, 不再走 article 的状态校验
        for (ArticleFavorite f : favorites) {
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, f.getArticleId())
                    .setSql("favorite_count = GREATEST(favorite_count - 1, 0)"));
            articleHotRankingService.incrementScore(f.getArticleId(), -Constant.HOT_SCORE_WEIGHT_FAVORITE);
        }
        log.info("用户 {} 删除收藏夹 {} 完成, 联动清理 {} 条收藏记录", loginUserId, folderId, favorites.size());
    }

    // ============================================================
    // 查询
    // ============================================================
    @Override
    public List<FolderVO> queryMyFolders(Long loginUserId) {
        if (loginUserId == null || loginUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        ensureDefaultFolder(loginUserId);
        List<UserFavoriteFolder> rows = folderMapper.selectList(new LambdaQueryWrapper<UserFavoriteFolder>()
                .eq(UserFavoriteFolder::getUserId, loginUserId)
                .ne(UserFavoriteFolder::getDeleteState, DELETE_YES)
                .orderByAsc(UserFavoriteFolder::getSortOrder)
                .orderByAsc(UserFavoriteFolder::getCreateTime));
        return rows.stream().map(f -> new FolderVO(f, true)).collect(Collectors.toList());
    }

    @Override
    public List<FolderVO> queryUserPublicFolders(Long userId, Long loginUserId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        boolean owner = Objects.equals(userId, loginUserId);
        LambdaQueryWrapper<UserFavoriteFolder> q = new LambdaQueryWrapper<>();
        q.eq(UserFavoriteFolder::getUserId, userId)
                .ne(UserFavoriteFolder::getDeleteState, DELETE_YES)
                .orderByAsc(UserFavoriteFolder::getSortOrder)
                .orderByAsc(UserFavoriteFolder::getCreateTime);
        if (!owner) {
            q.eq(UserFavoriteFolder::getIsPublic, PUBLIC_YES);
        }
        List<UserFavoriteFolder> rows = folderMapper.selectList(q);
        return rows.stream().map(f -> new FolderVO(f, owner)).collect(Collectors.toList());
    }

    // ============================================================
    // 默认夹懒加载
    // ============================================================
    @Override
    public Long ensureDefaultFolder(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        List<UserFavoriteFolder> exists = folderMapper.selectPage(new Page<>(1, 1, false),
                new LambdaQueryWrapper<UserFavoriteFolder>()
                .eq(UserFavoriteFolder::getUserId, userId)
                .eq(UserFavoriteFolder::getIsDefault, DEFAULT_YES)
                .ne(UserFavoriteFolder::getDeleteState, DELETE_YES)).getRecords();
        UserFavoriteFolder exist = exists.isEmpty() ? null : exists.get(0);
        if (exist != null) {
            return exist.getId();
        }
        UserFavoriteFolder folder = new UserFavoriteFolder();
        folder.setUserId(userId);
        folder.setName("默认收藏夹");
        folder.setIsPublic(PUBLIC_YES);
        folder.setIsDefault(DEFAULT_YES);
        folder.setSortOrder(0);
        folder.setItemCount(0);
        try {
            folderMapper.insert(folder);
            log.info("为用户 {} 创建默认收藏夹 id={}", userId, folder.getId());
            return folder.getId();
        } catch (DuplicateKeyException dup) {
            // 并发场景: 极端情况下两次调用可能同时走 insert; 退化为再查一次
            List<UserFavoriteFolder> againRows = folderMapper.selectPage(new Page<>(1, 1, false),
                    new LambdaQueryWrapper<UserFavoriteFolder>()
                    .eq(UserFavoriteFolder::getUserId, userId)
                    .eq(UserFavoriteFolder::getIsDefault, DEFAULT_YES)
                    .ne(UserFavoriteFolder::getDeleteState, DELETE_YES)).getRecords();
            UserFavoriteFolder again = againRows.isEmpty() ? null : againRows.get(0);
            return again == null ? null : again.getId();
        }
    }

    // ============================================================
    // 共通: 校验夹归属与可见性
    // ============================================================
    @Override
    public UserFavoriteFolder requireFolder(Long folderId, Long loginUserId, boolean mustOwn) {
        if (folderId == null || folderId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        UserFavoriteFolder folder = folderMapper.selectOne(new LambdaQueryWrapper<UserFavoriteFolder>()
                .eq(UserFavoriteFolder::getId, folderId)
                .ne(UserFavoriteFolder::getDeleteState, DELETE_YES));
        if (folder == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FOLDER_NOT_EXISTS));
        }
        boolean owner = Objects.equals(folder.getUserId(), loginUserId);
        if (mustOwn && !owner) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FOLDER_NO_PERMISSION));
        }
        // 只读场景: 私密夹仅本人可见, 其他人按"不存在"返回, 避免被探测出私密夹列表
        if (!mustOwn && !owner && folder.getIsPublic() != null && folder.getIsPublic() == PUBLIC_NO) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FOLDER_NOT_EXISTS));
        }
        return folder;
    }

    // 公开/私密切换限流，防止恶意刷接口
    private void assertFolderVisibilityChangeAllowed(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        String key = REDIS_FOLDER_VISIBILITY_CHANGES + userId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, FOLDER_VISIBILITY_WINDOW_MS, TimeUnit.MILLISECONDS);
        }
        if (count != null && count > FOLDER_VISIBILITY_CHANGE_LIMIT) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FOLDER_VISIBILITY_RATE_LIMIT));
        }
    }
}
