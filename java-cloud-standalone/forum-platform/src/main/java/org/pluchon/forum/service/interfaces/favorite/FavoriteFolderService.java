package org.pluchon.forum.service.interfaces.favorite;

import org.pluchon.forum.entity.db.UserFavoriteFolder;
import org.pluchon.forum.entity.dto.favorite.CreateFolderRequest;
import org.pluchon.forum.entity.dto.favorite.UpdateFolderRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.favorite.FolderVO;

/**
 * 收藏夹: 增删改查 + 默认夹懒加载.
 * 仅作者本人可写; 公开夹任何人可读, 私密夹仅作者本人可读.
 */
public interface FavoriteFolderService {

    /** 创建新收藏夹, 返回新夹ID. */
    Long createFolder(CreateFolderRequest req, Long loginUserId);

    /** 修改夹的名字 / 公开性 / 排序; 默认夹也可改名 / 改公开性, 但不能取消 isDefault. */
    void updateFolder(UpdateFolderRequest req, Long loginUserId);

    /**
     * 删除收藏夹(软删):
     *  - 默认夹不允许删
     *  - 同时把夹内 article_favorite 全部软删, 并扣减对应 article.favorite_count
     *  - 同步从热帖榜扣对应分
     */
    void deleteFolder(Long folderId, Long loginUserId);

    /** 分页查询我的收藏夹(含默认夹), 按 sortOrder, createTime 升序. */
    PageResult<FolderVO> queryMyFolders(Long loginUserId, Integer pageNum, Integer pageSize);

    /** 分页查看他人公开夹列表; userId == loginUserId 时包含私密夹. */
    PageResult<FolderVO> queryUserPublicFolders(Long userId, Long loginUserId,
                                                Integer pageNum, Integer pageSize);

    /**
     * 保证当前用户有一个 is_default=1 的夹. 注册流程调用一次;
     * 老用户登录时若发现没有默认夹也会被服务层兜底创建.
     * 返回该默认夹 ID(已存在直接返回, 不重复创建).
     */
    Long ensureDefaultFolder(Long userId);

    /**
     * 校验该夹归属与可见性, 失败抛 ApplicationException.
     *  - mustOwn=true: 必须是本人; 否则抛 1148
     *  - mustOwn=false: 公开夹任何人通过; 私密夹仅本人通过
     */
    UserFavoriteFolder requireFolder(Long folderId, Long loginUserId, boolean mustOwn);
}
