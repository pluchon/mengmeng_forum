package org.pluchon.forum.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

/**
 * @author pluchon
 * @create 2026-03-09-15:59
 * 作者代码水平一般，难免难看，请见谅
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleReplyListResponse {
    private ArticleReply articleReply;
    private UserBriefVO user;
    // 楼中楼回复数
    private Integer subReplyCount;
    // 当前登录用户是否已点赞
    private Boolean liked;
    private java.util.List<ArticleReplyMediaVO> mediaList;
}
