package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.MessageAlbumImage;

// 私信图集图片 Mapper
@Mapper
public interface MessageAlbumImageMapper extends BaseMapper<MessageAlbumImage> {
}
