package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.UserFavoriteFolder;

@Mapper
public interface UserFavoriteFolderMapper extends BaseMapper<UserFavoriteFolder> {

    // 收藏数原子 +1 仅未删除夹
    @Update("UPDATE user_favorite_folder SET item_count = item_count + 1 "
            + "WHERE id = #{folderId} AND delete_state = 0")
    void incrementItemCount(@Param("folderId") Long folderId);

    // 收藏数原子 1 item_count 已 >0 才扣, 避免变负值
    @Update("UPDATE user_favorite_folder SET item_count = item_count - 1 "
            + "WHERE id = #{folderId} AND delete_state = 0 AND item_count > 0")
    void decrementItemCount(@Param("folderId") Long folderId);
}
