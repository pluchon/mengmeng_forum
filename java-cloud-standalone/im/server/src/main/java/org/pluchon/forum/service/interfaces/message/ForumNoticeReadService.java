package org.pluchon.forum.service.interfaces.message;

import org.pluchon.forum.entity.vo.ForumNoticeCenterItemVO;

import java.util.List;

public interface ForumNoticeReadService {

    // 已发布且未删除的公告，供用户端公告中心展示
    List<ForumNoticeCenterItemVO> listPublishedForCenter();
}
