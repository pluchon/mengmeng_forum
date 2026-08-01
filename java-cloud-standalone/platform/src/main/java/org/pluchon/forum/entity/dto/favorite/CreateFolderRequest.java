package org.pluchon.forum.entity.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建收藏夹请求")
public class CreateFolderRequest {

    @NotBlank(message = "收藏夹名称不能为空")
    @Size(max = 25, message = "收藏夹名称不能超过 25 字")
    @Schema(description = "收藏夹名称", example = "我的精选")
    private String name;

    /** 0 私密 1 公开; 不传按 1 处理 */
    @Schema(description = "公开性: 0私密 1公开, 默认 1", example = "1")
    private Byte isPublic;
}
