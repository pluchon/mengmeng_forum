package org.pluchon.forum.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// 楼中楼回复列表 VO，每条子回复 + 发言用户信息 + 被回复用户的昵称
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleSubReplyListResponse {
    private ArticleSubReply subReply;
    private UserBriefVO postUser;
    private String replyUserNickname;
    // 当前登录用户是否已点赞
    private Boolean liked;
    // 问答帖：是否已被楼主采纳
    private Boolean accepted;
    // 审核违规软删占位
    private Boolean violated;
    private java.util.List<ArticleReplyMediaVO> mediaList;
}
