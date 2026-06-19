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
    //前端需要显示帖子回复信息以及贴子发布者的信息
    private ArticleReply articleReply;
    private UserBriefVO user;
}
