package org.pluchon.forum.entity.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "把已收藏帖子在自己的收藏夹之间移动")
public class MoveFavoriteRequest {

    @NotNull(message = "articleId 不能为空")
    @Schema(description = "要移动的帖子ID", example = "100")
    private Long articleId;

    @NotNull(message = "toFolderId 不能为空")
    @Schema(description = "目标收藏夹ID(必须属于当前用户)", example = "2")
    private Long toFolderId;
}
