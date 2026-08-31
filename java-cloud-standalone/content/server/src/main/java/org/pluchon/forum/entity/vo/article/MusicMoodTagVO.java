package org.pluchon.forum.entity.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "音乐氛围标签")
public class MusicMoodTagVO {

    private String name;

    @Schema(description = "BUILTIN 内置 / AI 补充 / USER 创作者创建")
    private String source;

    private Integer useCount;
}
