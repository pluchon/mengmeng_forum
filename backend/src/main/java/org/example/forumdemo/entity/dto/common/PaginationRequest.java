package org.example.forumdemo.entity.dto.common;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-04-18-13:00
 * 作者代码水平一般，难免难看，请见谅
 */
//前端分页请求
@Data
public class PaginationRequest {
    //页号，默认从一开始
    private Integer pageNum = 1;
    //每页十条内容
    private Integer pageSize = 10;
}
