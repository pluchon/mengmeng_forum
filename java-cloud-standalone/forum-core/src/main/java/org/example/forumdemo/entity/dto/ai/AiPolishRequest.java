package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

// 帖子正文一键润色请求
@Data
public class AiPolishRequest {

    /** 由服务端按会员档位确定，客户端传值会被忽略。 */
    private String kind;

    private String title;

    private String content;

    private String editorMode;

    /** 是否使用萌币积分，默认由会员额度承担 */
    private Boolean usePointsBilling;

    /** 客户端幂等键，重试时必须相同 */
    private String clientRequestId;

    /** AI 创作工作区；为空时由服务端创建 */
    private Long workspaceId;

    /** 生成结果所属父版本 */
    private Long parentVersionId;

    /** LangGraph checkpoint 标识，仅用于恢复关联 */
    private String checkpointId;
}
