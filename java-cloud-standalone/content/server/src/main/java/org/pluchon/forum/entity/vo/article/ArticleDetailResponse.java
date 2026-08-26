package org.pluchon.forum.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Collections;
import java.util.List;

// 作者代码水平一般，难免难看，请见谅
// 帖子详情信息，包含用户信息和帖子信息和板块信息，前端可以进行处理，便于后续拓展
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailResponse {
    private UserBriefVO user;
    private Article article;
    private Board board;
    // 加入检测是否是作者，便于前端展示是否编辑的按钮
    private Boolean isOwner;
    // 当前登录用户是否已点赞此帖子
    private Boolean isLiked;
    // 当前登录用户是否已收藏此帖子
    private Boolean isFavorited;
    // 当前登录用户是否设为不感兴趣
    private Boolean isNotInterested;
    // 相册图URL列表, 按 sort 升序; 老帖 / 无图帖返回空数组
    private List<String> imageUrls;

    // 帖子标签 扁平展示
    private List<ArticleTagVO> tags;

    // 兼容旧的 6 参构造调用; imageUrls/tags 默认空列表, 调用方按需 set 注入
    public ArticleDetailResponse(UserBriefVO user, Article article, Board board, Boolean isOwner, Boolean isLiked, Boolean isFavorited) {
        this(user, article, board, isOwner, isLiked, isFavorited, false, Collections.emptyList(), Collections.emptyList());
    }
}
