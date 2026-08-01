package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 帖子相册图片表实体, 对应 article_image.
 * 一篇 article 对应 0~15 条记录, 由 ArticleService.replaceArticleImages 全量替换式维护.
 */
@Data
@TableName("article_image")
@Schema(description = "帖子相册图片实体")
public class ArticleImage {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属帖子ID")
    private Long articleId;

    @Schema(description = "相册图URL")
    private String imageUrl;

    @Schema(description = "相册内排序, 0 在最前")
    private Integer sort;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
