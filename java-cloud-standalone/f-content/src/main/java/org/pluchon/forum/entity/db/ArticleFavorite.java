package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("article_favorite")
@Schema(description = "帖子收藏记录实体")
public class ArticleFavorite {

    @Schema(description = "id值", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户的ID", example = "1")
    private Long userId;

    @Schema(description = "所属收藏夹ID", example = "1")
    private Long folderId;

    @Schema(description = "文章的ID", example = "1")
    private Long articleId;

    @Schema(description = "是否删除: 0否 1是", example = "0")
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
