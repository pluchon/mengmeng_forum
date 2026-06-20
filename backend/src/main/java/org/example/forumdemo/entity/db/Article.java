package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 帖子表实体类，对应 article
 */
@Data
@TableName("article")
@Schema(description = "帖子实体")
public class Article {

    @Schema(description = "帖子ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属版块ID", example = "1")
    private Long boardId;

    @Schema(description = "发帖人用户ID", example = "10")
    private Long userId;

    @Schema(description = "帖子标题", example = "分享一个 Spring Boot 3 实战项目")
    private String title;

    @Schema(description = "帖子正文内容")
    private String content;

    @Schema(description = "访问量", example = "520")
    private Integer visitCount;

    @Schema(description = "回复数", example = "36")
    private Integer replyCount;

    @Schema(description = "点赞数", example = "88")
    private Integer likeCount;

    @Schema(description = "封面图URL")
    private String coverImg;

    @Schema(description = "帖子媒体类型: 0图片相册 1视频(单个)", example = "0")
    private Byte mediaType;

    @Schema(description = "视频URL(仅 media_type=1 时有效)")
    private String videoUrl;

    @Schema(description = "内容类型: 0富文本 1Markdown", example = "0")
    private Byte contentType;

    @Schema(description = "收藏数", example = "0")
    private Integer favoriteCount;

    @Schema(description = "楼中楼回复数(独立于 replyCount, 楼层数仍只算一级回复)", example = "0")
    private Integer subReplyCount;

    @Schema(description = "审核状态: 0正常 1禁用", example = "0")
    private Byte state;

    @Schema(description = "发布状态: 0草稿 1审核中 2审核通过(瞬态) 3审核未通过 4审核异常 5已发布", example = "5")
    private Byte status;

    @Schema(description = "当前审核任务ID(UUID), 与 Python LangGraph thread_id 关联")
    private String auditTaskId;

    @Schema(description = "审核结果是否额外推邮件: 0否 1是", example = "0")
    private Byte auditNotifyEmail;

    @Schema(description = "当前累计提交审核次数(0~3)", example = "0")
    private Integer auditRetryCount;

    @Schema(description = "最近一次审核结论文本(通过原因/拒绝理由)")
    private String auditResultMessage;

    @Schema(description = "最近一次审核提交时间")
    private Date auditSubmittedAt;

    @Schema(description = "最近一次审核结束时间")
    private Date auditFinishedAt;

    @Schema(description = "发帖时IP属地快照")
    private String ipRegion;

    @Schema(description = "是否删除: 0否 1是", example = "0")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
