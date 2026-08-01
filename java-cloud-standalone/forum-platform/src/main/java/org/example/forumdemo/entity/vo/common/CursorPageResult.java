package org.example.forumdemo.entity.vo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 游标分页响应：高历史量列表深分页 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageResult<T> {

    private List<T> records;

    /** 下一页游标，null 表示没有更多 */
    private String nextCursor;

    private Boolean hasNextPage;

    private Integer pageSize;
}
