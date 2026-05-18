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
    //楼中楼回复记录
    private ArticleSubReply subReply;
    //发言用户信息
    private UserBriefVO postUser;
    //被回复的目标用户昵称
    private String replyUserNickname;
}
