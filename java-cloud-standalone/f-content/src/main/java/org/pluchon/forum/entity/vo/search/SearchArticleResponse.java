package org.pluchon.forum.entity.vo.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;

/**
 * 帖子搜索响应. source 字段标记本次结果来源:
 *  - db    : 标题 LIKE 匹配
 *  - rag   : DB 未命中, AI 语义召回
 *  - empty : 两路均无结果
 * 前端可据此切换 "为你智能推荐" 之类的提示文案.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "帖子搜索响应")
public class SearchArticleResponse {

    @Schema(description = "结果来源", example = "db")
    private String source;

    @Schema(description = "原始查询关键词", example = "Spring Boot")
    private String keyword;

    @Schema(description = "分页结果")
    private PageResult<ArticleListResponse> page;
}
