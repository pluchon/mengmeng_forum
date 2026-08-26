package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;

import java.util.List;

// AI 配乐匹配结果
@Data
public class AiHubMusicMatchResultVO {

    private List<String> musicKeys;

    private String rationale;

    private List<String> moods;

    private List<AiModelUsageDTO> usageItems;
}
