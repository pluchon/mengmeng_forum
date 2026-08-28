package org.pluchon.forum.entity.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 音频一键解析结果（不落库、不上传 OSS）
@Data
@Schema(description = "音频元数据解析结果")
public class MusicParseResultVO {

    private String title;

    private String artist;

    private String album;

    private String durationText;

    private Integer durationSeconds;

    private String lyricText;

    private Boolean hasCover;

    // 内嵌封面 MIME，如 image/jpeg
    private String coverMimeType;

    // 内嵌封面 Base64（不含 data: 前缀）
    private String coverBase64;
}
