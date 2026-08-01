package org.example.forumdemo.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.forumdemo.entity.db.ArticleSubReply;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

/**
 * @author pluchon
 * 楼中楼回复列表 VO，每条子回复 + 发言用户信息 + 被回复用户的昵称
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleSubReplyListResponse {
    private ArticleSubReply subReply;
    private UserBriefVO postUser;
    private String replyUserNickname;
    // 当前登录用户是否已点赞
    private Boolean liked;
    private java.util.List<ArticleReplyMediaVO> mediaList;
}
