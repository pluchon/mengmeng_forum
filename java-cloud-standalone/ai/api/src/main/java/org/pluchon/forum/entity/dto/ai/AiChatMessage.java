package org.pluchon.forum.entity.dto.ai;

import lombok.Data;

@Data
public class AiChatMessage {
    private String role;
    private String content;
}
