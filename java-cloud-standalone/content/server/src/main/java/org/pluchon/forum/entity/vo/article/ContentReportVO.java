package org.pluchon.forum.entity.vo.article;

import lombok.Data;

// 举报提交结果
@Data
public class ContentReportVO {

    private Long reportId;
    private String status;
    private String message;
}
