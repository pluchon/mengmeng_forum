package org.example.forumdemo.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.entity.vo.common.PageResult;

/**
 * 用户帖子列表分页响应（包含用户信息和owner标志）
 *
 * @author pluchon
 * @create 2026-04-18
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleListByUserIdPageResponse extends PageResult<Article> {
    private UserBriefVO user;
    private Boolean isOwner;

    // 便于构造：将PageResult转换为此类
    public ArticleListByUserIdPageResponse(PageResult<Article> pageResult, UserBriefVO user, Boolean isOwner) {
        super(pageResult.getRecords(), pageResult.getTotal(), pageResult.getPageNum(),
              pageResult.getPageSize(), pageResult.getPages(), pageResult.getHasNextPage());
        this.user = user;
        this.isOwner = isOwner;
    }
}
