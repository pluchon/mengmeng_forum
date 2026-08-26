package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 视频帖弹幕
@Data
@TableName("article_video_danmaku")
public class ArticleVideoDanmaku {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 帖子 ID
    private Long articleId;

    // 发送用户 ID
    private Long userId;

    // 弹幕对应视频时间点 毫秒
    private Integer videoTimeMs;

    // 弹幕文本
    private String content;

    // 预设颜色编码
    private Byte colorCode;

    // 弹幕模式：0 滚动 1 顶部 2 底部
    private Byte mode;

    // 字号：0 小 1 标准
    private Byte fontSize;

    // 点赞数
    private Integer likeCount;

    // 逻辑删除：0 正常 1 已删除
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
