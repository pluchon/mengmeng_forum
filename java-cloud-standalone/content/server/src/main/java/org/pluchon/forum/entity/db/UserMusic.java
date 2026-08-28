package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 用户上传歌曲
@Data
@TableName("user_music")
@Schema(description = "用户上传歌曲")
public class UserMusic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String musicKey;

    private String title;

    private String artist;

    private String album;

    private String durationText;

    private String coverUrl;

    private String audioUrl;

    private String lrcUrl;

    private String lyricText;

    private String moodTags;

    private String aiProfile;

    private String reviewResult;

    private Date aiAnalyzedAt;

    // 0未发布 1审核中 2已发布 3未通过
    private Byte status;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
