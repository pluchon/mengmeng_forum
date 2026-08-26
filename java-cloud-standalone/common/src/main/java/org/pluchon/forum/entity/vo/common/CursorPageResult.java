package org.pluchon.forum.entity.vo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 游标分页响应结果
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageResult<T> {
    // 记录列表
    private List<T> records;
    // 下一页游标
    private String nextCursor;
    // 是否有下一页
    private Boolean hasNextPage;
    // 每页条数
    private Integer pageSize;
}
