package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.ArticleVideoDanmaku;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.vo.article.DanmakuItemVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 弹幕 Entity 与 VO 转换
public final class DanmakuConverter {

    private DanmakuConverter() {
    }

    public static DanmakuItemVO toItemVO(ArticleVideoDanmaku entity, UserInternalVO user) {
        if (entity == null) {
            return null;
        }
        DanmakuItemVO vo = new DanmakuItemVO();
        vo.setId(entity.getId());
        vo.setArticleId(entity.getArticleId());
        vo.setUserId(entity.getUserId());
        vo.setVideoTimeMs(entity.getVideoTimeMs());
        vo.setContent(entity.getContent());
        vo.setColorCode(entity.getColorCode());
        vo.setMode(entity.getMode());
        vo.setFontSize(entity.getFontSize());
        vo.setLikeCount(entity.getLikeCount() == null ? 0 : entity.getLikeCount());
        vo.setCreateTime(entity.getCreateTime());
        if (user != null) {
            vo.setNickname(user.getNickname());
        }
        return vo;
    }

    public static List<DanmakuItemVO> toItemVOList(List<ArticleVideoDanmaku> entities, Map<Long, UserInternalVO> userMap) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<DanmakuItemVO> list = new ArrayList<>(entities.size());
        for (ArticleVideoDanmaku entity : entities) {
            UserInternalVO user = userMap != null ? userMap.get(entity.getUserId()) : null;
            list.add(toItemVO(entity, user));
        }
        return list;
    }
}
