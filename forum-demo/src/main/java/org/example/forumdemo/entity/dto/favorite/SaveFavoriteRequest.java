package org.example.forumdemo.entity.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "收藏帖子请求")
public class SaveFavoriteRequest {

    @NotNull(message = "articleId 不能为空")
    @Schema(description = "要收藏的帖子ID", example = "100")
    private Long articleId;

    /** 留空则落到该用户的默认夹 */
    @Schema(description = "目标收藏夹ID, 留空则自动落到默认夹", example = "1")
    private Long folderId;
}
