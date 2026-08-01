package org.pluchon.forum.entity.vo.article;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;
import java.util.List;

// 问答帖最佳答案展示信息
@Data
public class QuestionAnswerVO {

    // 一级回答 ID
    private Long replyId;

    // 所属问答帖 ID
    private Long articleId;

    // 回答者公开信息
    private UserBriefVO user;

    // 回答正文
    private String content;

    // 回答时 IP 属地
    private String ipRegion;

    // 点赞数
    private Integer likeCount;

    // 楼中楼数量
    private Integer subReplyCount;

    // 当前用户是否点赞
    private Boolean liked;

    // 回答媒体列表
    private List<ArticleReplyMediaVO> mediaList;

    // 回答时间
    private Date createTime;
}
