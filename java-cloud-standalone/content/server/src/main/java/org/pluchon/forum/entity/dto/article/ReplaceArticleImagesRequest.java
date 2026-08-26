package org.pluchon.forum.entity.dto.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 全量替换帖子相册图请求
@Data
@Schema(description = "全量替换帖子相册请求")
public class ReplaceArticleImagesRequest {

    // 帖子ID
    @Schema(description = "帖子ID", example = "123")
    private Long articleId;

    // 相册图片URL列表
    @Schema(description = "相册图片URL列表，传空数组表示清空相册")
    private List<String> imageUrls;
}
