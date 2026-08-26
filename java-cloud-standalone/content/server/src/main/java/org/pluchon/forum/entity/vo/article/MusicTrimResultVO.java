package org.pluchon.forum.entity.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 音频裁剪结果（不落库、不上传 OSS）
@Data
@Schema(description = "音频裁剪结果")
public class MusicTrimResultVO {

    private String fileName;

    private String mimeType;

    private String durationText;

    private Integer durationSeconds;

    // 裁剪后音频 Base64（不含 data: 前缀）
    private String audioBase64;
}
