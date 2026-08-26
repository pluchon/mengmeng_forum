package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 楼中楼回复表实体类，对应 article_sub_reply
@Data
@TableName("article_sub_reply")
@Schema(description = "楼中楼回复实体")
public class ArticleSubReply {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属帖子ID")
    private Long articleId;

    @Schema(description = "所属的一级回复(楼层)ID")
    private Long replyId;

    @Schema(description = "当前发言的用户ID")
    private Long postUserId;

    @Schema(description = "被回复的目标用户ID（用于显示 @昵称）")
    private Long replyUserId;

    @Schema(description = "回复内容（纯文本）")
    private String content;

    @Schema(description = "楼中楼回复时IP属地快照")
    private String ipRegion;

    @Schema(description = "点赞数", example = "0")
    private Integer likeCount;

    @JsonIgnore
    @Schema(description = "状态: 0-正常, 1-禁用")
    private Byte state;

    @JsonIgnore
    @Schema(description = "是否删除: 0-否, 1-是")
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
