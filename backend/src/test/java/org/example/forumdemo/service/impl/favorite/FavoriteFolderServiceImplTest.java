package org.example.forumdemo.service.impl.favorite;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.entity.db.UserFavoriteFolder;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.favorite.FolderVO;
import org.example.forumdemo.mapper.UserFavoriteFolderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 收藏夹分页查询测试
class FavoriteFolderServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void publicFolderListShouldReturnRequestedBackendPage() {
        UserFavoriteFolderMapper mapper = mock(UserFavoriteFolderMapper.class);
        FavoriteFolderServiceImpl service = new FavoriteFolderServiceImpl();
        ReflectionTestUtils.setField(service, "folderMapper", mapper);

        UserFavoriteFolder folder = new UserFavoriteFolder();
        folder.setId(3L);
        folder.setUserId(8L);
        folder.setName("测试收藏夹");
        folder.setIsPublic((byte) 1);

        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<UserFavoriteFolder> page = invocation.getArgument(0);
            page.setRecords(List.of(folder));
            page.setTotal(6L);
            return page;
        });

        PageResult<FolderVO> result = service.queryUserPublicFolders(8L, -1L, 2, 5);

        assertEquals(2, result.getPageNum());
        assertEquals(5, result.getPageSize());
        assertEquals(6L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }
}
