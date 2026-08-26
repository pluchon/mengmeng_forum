package org.pluchon.forum.entity.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

// 音乐大厅个人品味片单 AI 请求（收藏/近播信号 + 候选池）
@Data
public class AiMusicTasteRecommendRequest {

    private Long userId;

    private List<Map<String, Object>> favorites;

    private List<Map<String, Object>> recentPlays;

    private List<Map<String, Object>> extras;

    @NotEmpty
    private List<Map<String, Object>> candidates;
}
