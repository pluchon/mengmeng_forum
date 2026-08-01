package org.example.forumdemo.service.impl.remote;

import org.example.forum.cloud.feign.FavoriteFolderInternalFeignClient;
import org.example.forumdemo.entity.db.UserFavoriteFolder;
import org.example.forumdemo.entity.dto.favorite.CreateFolderRequest;
import org.example.forumdemo.entity.dto.favorite.UpdateFolderRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.favorite.FolderVO;
import org.example.forumdemo.service.interfaces.favorite.FavoriteFolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// 非 content 域通过 Feign 调用收藏夹内部接口（目前仅注册默认夹）
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'content'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class FavoriteFolderRemoteService implements FavoriteFolderService {

    @Autowired
    private FavoriteFolderInternalFeignClient favoriteFolderInternalFeignClient;

    @Override
    public Long ensureDefaultFolder(Long userId) {
        return favoriteFolderInternalFeignClient.ensureDefaultFolder(userId);
    }

    @Override
    public Long createFolder(CreateFolderRequest req, Long loginUserId) {
        throw new UnsupportedOperationException("请走 content 服务创建收藏夹");
    }

    @Override
    public void updateFolder(UpdateFolderRequest req, Long loginUserId) {
        throw new UnsupportedOperationException("请走 content 服务更新收藏夹");
    }

    @Override
    public void deleteFolder(Long folderId, Long loginUserId) {
        throw new UnsupportedOperationException("请走 content 服务删除收藏夹");
    }

    @Override
    public PageResult<FolderVO> queryMyFolders(Long loginUserId, Integer pageNum, Integer pageSize) {
        throw new UnsupportedOperationException("请走 content 服务查询收藏夹");
    }

    @Override
    public PageResult<FolderVO> queryUserPublicFolders(Long userId, Long loginUserId,
                                                       Integer pageNum, Integer pageSize) {
        throw new UnsupportedOperationException("请走 content 服务查询收藏夹");
    }

    @Override
    public UserFavoriteFolder requireFolder(Long folderId, Long loginUserId, boolean mustOwn) {
        throw new UnsupportedOperationException("请走 content 服务校验收藏夹");
    }
}
