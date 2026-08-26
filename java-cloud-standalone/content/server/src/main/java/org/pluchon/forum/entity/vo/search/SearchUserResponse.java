package org.pluchon.forum.entity.vo.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.vo.common.PageResult;

// 用户搜索响应. source: db : 用户名 / 昵称 LIKE 匹配 rag : DB 未命中或 AI 模式, 语义召回 empty : 无结果
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户搜索响应")
public class SearchUserResponse {

    @Schema(description = "结果来源", example = "db")
    private String source;

    @Schema(description = "原始查询关键词")
    private String keyword;

    @Schema(description = "分页结果")
    private PageResult<SearchUserItemVO> page;
}
