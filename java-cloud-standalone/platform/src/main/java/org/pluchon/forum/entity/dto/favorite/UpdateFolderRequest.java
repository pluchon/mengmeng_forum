package org.pluchon.forum.entity.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新收藏夹请求(任一字段为空均视作不修改)")
public class UpdateFolderRequest {

    @NotNull(message = "folderId 不能为空")
    @Schema(description = "收藏夹ID", example = "1")
    private Long folderId;

    @Size(max = 25, message = "收藏夹名称不能超过 25 字")
    @Schema(description = "新名称(留空则不改名)", example = "技术干货")
    private String name;

    @Schema(description = "公开性: 0私密 1公开(null 则不改)", example = "0")
    private Byte isPublic;

    @Schema(description = "排序(null 则不改)", example = "1")
    private Integer sortOrder;
}
