package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 视频弹幕点赞
@Data
@TableName("article_video_danmaku_like")
public class ArticleVideoDanmakuLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long danmakuId;

    private Long userId;

    private Date createTime;
}
