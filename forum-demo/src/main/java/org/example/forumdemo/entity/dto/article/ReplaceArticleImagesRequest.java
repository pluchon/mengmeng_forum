package org.example.forumdemo.entity.dto.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 全量替换某个帖子的相册图. 服务端会:
 *   1) 校验登录用户 == 帖子作者
 *   2) 数量 ≤ 15, 每个 URL 必须落在 forum_article_picture/ 子目录
 *   3) 当 imageUrls 非空时, article.content 长度必须 ≥ 10 字符(防"图多字少"水帖)
 *   4) 软删旧 article_image 行, 按入参顺序插入新行(sort 从 0 自增)
 */
@Data
@Schema(description = "全量替换帖子相册请求")
public class ReplaceArticleImagesRequest {

    @Schema(description = "帖子ID", example = "123")
    private Long articleId;

    @Schema(description = "相册图URL列表(顺序即展示顺序), 必须是 /file/uploadArticleImage 返回的本站URL; 传空数组 = 清空相册")
    private List<String> imageUrls;
}
