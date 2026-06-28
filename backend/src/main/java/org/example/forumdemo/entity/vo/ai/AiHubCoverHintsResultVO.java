package org.example.forumdemo.entity.vo.ai;

import lombok.Data;

import java.util.List;

// AI Hub 封面配图要点响应
@Data
public class AiHubCoverHintsResultVO {

    private String content;
    private List<String> hints;
    private List<String> themes;
    private String summary;
}
