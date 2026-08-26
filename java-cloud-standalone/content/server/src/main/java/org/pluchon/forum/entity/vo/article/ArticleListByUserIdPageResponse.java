package org.pluchon.forum.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.entity.vo.common.PageResult;

// 用户帖子列表分页响应 包含用户信息和owner标志
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleListByUserIdPageResponse extends PageResult<ArticleBriefVO> {
    private UserBriefVO user;
    private Boolean isOwner;

    // 便于构造：将PageResult转换为此类
    public ArticleListByUserIdPageResponse(PageResult<ArticleBriefVO> pageResult, UserBriefVO user, Boolean isOwner) {
        super(pageResult.getRecords(), pageResult.getTotal(), pageResult.getPageNum(),
              pageResult.getPageSize(), pageResult.getPages(), pageResult.getHasNextPage());
        this.user = user;
        this.isOwner = isOwner;
    }
}
