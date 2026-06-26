package org.example.forumdemo.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.forumdemo.entity.db.ArticleReply;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

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
