package org.example.forumdemo.entity.vo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页响应包装类，适用于所有需要分页的列表查询
 * 泛型参数 T 可以是任何列表响应类型（ArticleListResponse、MessageListResponse 等）
 *
 * @author pluchon
 * @create 2026-04-18
 */
//分页请求
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {
    private List<T> records;      // 当前页的记录列表
    private Long total;           // 总记录数
    private Integer pageNum;      // 当前页号（从1开始）
    private Integer pageSize;     // 每页显示条数
    private Long pages;           // 总页数
    private Boolean hasNextPage;  // 是否有下一页
}
