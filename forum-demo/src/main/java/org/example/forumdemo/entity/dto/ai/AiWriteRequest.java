package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiWriteRequest {

    /**
     * deepseek_flash | deepseek_pro | qwen_flash | qwen_pro | gemini_pro | claude_haiku | claude_sonnet
     */
    private String kind;

    private List<AiChatMessage> messages;

    /** use points billing when quota is exhausted */
    private Boolean usePointsBilling;
}
