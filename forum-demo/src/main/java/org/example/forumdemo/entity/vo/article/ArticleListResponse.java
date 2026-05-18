package org.example.forumdemo.entity.vo.article;

import lombok.Data;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

/**
 * @author pluchon
 * @create 2026-03-08-16:35
 * 作者代码水平一般，难免难看，请见谅
 */
//返回给前端的首页的内容，可以重复利用
//如果前端传入0，则代表的是首页展示的全部帖子
//如果是其他的，则是对应板块的帖子内容
@Data
public class ArticleListResponse {
    //我们在各个类中已经抹去了敏感信息，放心组装返回
    private UserBriefVO user;
    private Article article;
}
