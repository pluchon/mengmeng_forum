package org.pluchon.forum.service.interfaces.favorite;

import org.pluchon.forum.entity.dto.favorite.MoveFavoriteRequest;
import org.pluchon.forum.entity.dto.favorite.SaveFavoriteRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.favorite.FolderArticleVO;

// 帖子收藏的核心写入与查询. 同一用户 + 同一帖子只能收藏一次, 跨夹归属变更走 move 接口.
public interface FavoriteArticleService {

    // 收藏帖子到指定夹. folderId 留空 → 落到当前用户的默认夹 必要时自动创建 已收藏过同篇帖子 → 抛 1150 前端可改为 再次点收藏 取消 的 UI 事务内: 插入 article_favorite + folder.item_count + article.favorite_count + 热帖榜 score 返回最终落地的 folderId, 方便前端弹 已收藏到 xx夹 的 Toast.
    Long saveFavorite(SaveFavoriteRequest req, Long loginUserId);

    // 取消收藏 系统自动定位所在夹 , 已未收藏则抛 1151. 事务内: 软删 article_favorite + folder.item_count + article.favorite_count + 热帖榜 score.
    void cancelFavorite(Long articleId, Long loginUserId);

    // 跨夹移动 限本人 . 源/目的为同一夹时直接返回.
    void moveFavorite(MoveFavoriteRequest req, Long loginUserId);

    // 当前用户是否收藏过该帖子 用于详情页 isFavorited 字段 .
    boolean isFavorited(Long articleId, Long loginUserId);

    // 分页查询夹内帖子列表 按收藏时间倒序 . 公开夹: 任何人都能看 私密夹: 仅作者本人能看 否则 1148
    PageResult<FolderArticleVO> queryFolderArticles(Long folderId, Long loginUserId,
                                                    Integer pageNum, Integer pageSize);
}
