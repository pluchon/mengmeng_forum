package org.pluchon.forum.entity.vo.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 曲库/我的歌曲曲目
@Data
@Schema(description = "帖子曲库曲目")
public class MusicTrackVO {

    @Schema(description = "用户歌曲ID，曲库公共曲可空")
    private Long id;

    @Schema(description = "曲库键(文件名 stem)")
    // 上传者，前端据此隐藏「举报自己的歌」入口
    private Long userId;

    private String musicKey;

    @Schema(description = "歌名")
    private String title;

    @Schema(description = "歌手")
    private String artist;

    @Schema(description = "专辑")
    private String album;

    @Schema(description = "时长 mm:ss")
    private String durationText;

    @Schema(description = "封面 URL")
    private String coverUrl;

    @Schema(description = "音频 URL")
    private String audioUrl;

    @Schema(description = "歌词 URL，可空")
    private String lrcUrl;

    @Schema(description = "歌词正文，仅本人上传列表返回")
    private String lyricText;

    @Schema(description = "状态: draft/reviewing/published/rejected")
    private String status;

    @Schema(description = "AI 氛围标签")
    private List<String> moodTags;

    @Schema(description = "审核未通过原因摘要")
    private String reviewReason;

    @Schema(description = "审核结论类型: violation/service_error")
    private String reviewKind;

    @Schema(description = "是否 AI 推荐/搜索命中")
    private Boolean aiMatched;

    @Schema(description = "当前用户是否已收藏")
    private Boolean favorited;

    @Schema(description = "全站播放次数")
    // 原曲的实时可用性：ok 正常 / offline 已下架 / deleted 已删除。
    // 快照里的 audio_url 始终可播，这个字段只决定收藏夹里怎么显示
    private String availability;

    private Long playCount;

    @Schema(description = "播放量展示文案")
    private String playCountText;
}
