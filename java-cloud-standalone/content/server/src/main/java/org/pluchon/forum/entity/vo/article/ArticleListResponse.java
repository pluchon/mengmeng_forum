package org.pluchon.forum.entity.vo.article;

import lombok.Data;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// 作者代码水平一般，难免难看，请见谅
// 返回给前端的首页的内容，可以重复利用
// 如果前端传入0，则代表的是首页展示的全部帖子
// 如果是其他的，则是对应板块的帖子内容
@Data
public class ArticleListResponse {
    // 我们在各个类中已经抹去了敏感信息，放心组装返回
    private UserBriefVO user;
    private Article article;
    // 当前登录用户是否关注了该帖作者；未登录时为 false
    private Boolean fromFollowing;
    // 相册图片数量 未删除 ，图文帖角标用
    private Integer imageCount;
    // 相册第一张图 URL，卡片悬停预览用；无相册时为空
    private String firstImageUrl;
}
