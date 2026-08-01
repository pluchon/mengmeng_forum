package org.pluchon.forum.entity.dto.ai;

import lombok.Data;

@Data
public class AiImageRequest {

    private String prompt;

    /** normal | premium */
    private String quality;

    /** companion drawing session id (optional) */
    private String sessionId;

    /** article id for cover image generation (optional) */
    private Long articleId;

    /** skip persisting to companion session table */
    private Boolean ephemeral;

    /** use points billing when quota is exhausted */
    private Boolean usePointsBilling;

    /** 客户端幂等键，重试时必须相同 */
    private String clientRequestId;

    /** AI 创作工作区；用于保存封面候选版本 */
    private Long workspaceId;

    /** 生成结果所属父版本 */
    private Long parentVersionId;

    /** LangGraph checkpoint 标识，仅用于恢复关联 */
    private String checkpointId;
}
