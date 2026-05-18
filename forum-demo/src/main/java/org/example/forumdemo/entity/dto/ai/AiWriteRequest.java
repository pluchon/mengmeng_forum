package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiWriteRequest {
    /**
     * deepseek_flash / deepseek_pro / gemini_flash / gemini_pro
     */
    private String kind;
    private List<AiChatMessage> messages;
}
