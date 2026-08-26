package org.pluchon.forum.entity.vo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 通用分页响应结果
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {
    // 记录列表
    private List<T> records;
    // 总记录数
    private Long total;
    // 当前页码
    private Integer pageNum;
    // 每页条数
    private Integer pageSize;
    // 总页数
    private Long pages;
    // 是否有下一页
    private Boolean hasNextPage;
}
