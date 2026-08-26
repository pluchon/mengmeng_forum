package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

import java.util.List;

// AI 创作者数据小结结果
@Data
public class AiHubCreatorInsightResultVO {

    private String headline;

    private String overview;

    private String highlight;

    private List<String> highlights;
}
