package org.pluchon.forum.entity.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

// 编辑已发布表情包请求，字段规则与创建请求一致
@Schema(description = "编辑已发布表情包请求")
public class UpdateEmojiShopRequest extends CreateEmojiShopRequest {
}
