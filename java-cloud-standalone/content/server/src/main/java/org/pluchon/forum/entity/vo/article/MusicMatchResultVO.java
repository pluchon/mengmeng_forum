package org.pluchon.forum.entity.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 配乐 AI 匹配结果
@Data
@Schema(description = "配乐 AI 匹配结果")
public class MusicMatchResultVO {

    @Schema(description = "匹配曲目")
    private List<MusicTrackVO> tracks;

    @Schema(description = "AI 说明")
    private String rationale;

    @Schema(description = "识别到的氛围标签")
    private List<String> moods;

    @Schema(description = "空结果提示")
    private String emptyHint;
}
