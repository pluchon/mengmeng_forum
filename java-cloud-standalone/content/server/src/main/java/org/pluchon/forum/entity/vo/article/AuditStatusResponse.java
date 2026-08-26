package org.pluchon.forum.entity.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 帖子审核状态响应. 前端在 审核中 页面用此接口轮询兜底.
@Data
@Schema(description = "帖子审核状态查询响应")
public class AuditStatusResponse {

    @Schema(description = "帖子ID", example = "12")
    private Long articleId;

    @Schema(description = "当前帖子状态: 0草稿 1审核中 2审核通过(瞬态) 3审核未通过 4审核异常 5已发布")
    private Byte status;

    @Schema(description = "状态文字描述")
    private String statusText;

    @Schema(description = "当前审核任务ID; 为空说明从未提交过")
    private String taskId;

    @Schema(description = "最近一次审核结论文本(通过原因/拒绝理由), 可空")
    private String resultMessage;

    @Schema(description = "累计已提交次数(0~3)")
    private Integer retryCount;

    @Schema(description = "审核次数上限")
    private Integer retryLimit;

    @Schema(description = "已达上限不能再提交")
    private Boolean retryLimitReached;

    @Schema(description = "最近一次审核提交时间")
    private Date submittedAt;

    @Schema(description = "最近一次审核结束时间; 当前 PENDING 状态则为空")
    private Date finishedAt;
}
