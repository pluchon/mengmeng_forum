package org.example.forumdemo.entity.vo.article;

import lombok.Data;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

import java.util.List;

/**
 * @author pluchon
 * @create 2026-03-10-08:26
 * 作者代码水平一般，难免难看，请见谅
 */
//指定用户的帖子列表，包括用户信息和帖子列表信息
@Data
public class ArticleListByUserIdResponse {
    private UserBriefVO user;
    private List<Article> articleList;
    private Boolean isOwner;
}
