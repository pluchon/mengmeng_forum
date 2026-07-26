package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiWriteRequest {

    /** 由服务端按会员档位确定，客户端传值会被忽略。 */
    private String kind;

    private List<AiChatMessage> messages;

    /** use points billing when quota is exhausted */
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
