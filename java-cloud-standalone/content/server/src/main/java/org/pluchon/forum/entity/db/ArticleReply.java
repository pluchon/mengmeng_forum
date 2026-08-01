package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 帖子回复表实体类，对应 article_reply
 */
@Data
@TableName("article_reply")
@Schema(description = "帖子回复实体")
public class ArticleReply {

    @Schema(description = "回复ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属帖子ID", example = "5")
    private Long articleId;

    @Schema(description = "回帖用户ID", example = "10")
    private Long postUserId;

    @Schema(description = "楼中楼: 关联的父级回复ID (null 表示一级回复)", example = "null")
    private Long replyId;

    @Schema(description = "楼中楼: 被回复的用户ID", example = "12")
    private Long replyUserId;

    @Schema(description = "回帖内容", example = "写得很好，学到了！")
    private String content;

    @Schema(description = "评论时IP属地快照")
    private String ipRegion;

    @Schema(description = "点赞数", example = "6")
    private Integer likeCount;

    @JsonIgnore
    @Schema(description = "状态: 0正常 1禁用", example = "0")
    private Byte state;

    @JsonIgnore
    @Schema(description = "是否删除: 0否 1是", example = "0")
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
