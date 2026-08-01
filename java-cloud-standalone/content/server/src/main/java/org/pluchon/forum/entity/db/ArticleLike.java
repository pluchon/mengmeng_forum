package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * @author pluchon
 * @create 2026-04-18-09:31
 * 作者代码水平一般，难免难看，请见谅
 */
//记录用户点赞了哪些帖子
@Data
@TableName("article_like")
@Schema(description = "用户喜欢的文章实体")
public class ArticleLike {
    @Schema(description = "id值",example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户的ID",example = "1")
    private Long userId;

    @Schema(description = "文章的ID",example = "1")
    private Long articleId;

    @Schema(description = "创建时间")
    private Date createTime;
}
