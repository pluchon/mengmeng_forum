package org.example.forumdemo.converter;

import org.example.forumdemo.common.enums.DriftBottleStatus;
import org.example.forumdemo.entity.db.DriftBottle;
import org.example.forumdemo.entity.db.DriftBottleComment;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleCommentVO;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleDetailVO;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleListItemVO;

import java.util.List;
import java.util.Objects;

// 漂流瓶实体转换
public final class DriftBottleConverter {

    private DriftBottleConverter() {
    }

    public static DriftBottleListItemVO toListItemVO(DriftBottle bottle, String latestComment) {
        if (bottle == null) {
            return null;
        }
        DriftBottleListItemVO vo = new DriftBottleListItemVO();
        vo.setId(bottle.getId());
        vo.setContent(bottle.getContent());
        vo.setMoodType(bottle.getMoodType());
        vo.setStatusText(DriftBottleStatus.textOf(bottle.getStatus()));
        vo.setCommentCount(bottle.getCommentCount());
        vo.setPickedCount(bottle.getPickedCount());
        vo.setLatestComment(latestComment);
        vo.setCreateTime(bottle.getCreateTime());
        return vo;
    }

    public static DriftBottleDetailVO toDetailVO(DriftBottle bottle, Long loginUserId, List<DriftBottleCommentVO> comments) {
        if (bottle == null) {
            return null;
        }
        DriftBottleDetailVO vo = new DriftBottleDetailVO();
        vo.setId(bottle.getId());
        vo.setContent(bottle.getContent());
        vo.setMoodType(bottle.getMoodType());
        vo.setStatusText(DriftBottleStatus.textOf(bottle.getStatus()));
        vo.setCommentCount(bottle.getCommentCount());
        vo.setPickedCount(bottle.getPickedCount());
        vo.setIsOwner(Objects.equals(bottle.getUserId(), loginUserId));
        vo.setCreateTime(bottle.getCreateTime());
        vo.setComments(comments);
        return vo;
    }

    public static DriftBottleCommentVO toCommentVO(DriftBottleComment comment, String anonymousName, Long loginUserId) {
        if (comment == null) {
            return null;
        }
        DriftBottleCommentVO vo = new DriftBottleCommentVO();
        vo.setId(comment.getId());
        vo.setAnonymousName(anonymousName);
        vo.setContent(comment.getContent());
        vo.setIsMine(Objects.equals(comment.getUserId(), loginUserId));
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}
