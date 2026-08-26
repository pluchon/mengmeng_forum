package org.pluchon.forum.entity.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

// 热榜曲目
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "音乐热榜曲目")
public class MusicHotTrackVO extends MusicTrackVO {

    @Schema(description = "榜单名次，从 1 起")
    private Integer rank;
}
