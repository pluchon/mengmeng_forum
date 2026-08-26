package org.pluchon.forum.entity.dto.article;

import lombok.Data;

// 发送弹幕请求
@Data
public class SendDanmakuRequest {

    // 帖子 ID
    private Long articleId;

    // 弹幕文本
    private String content;

    // 预设颜色编码
    private Byte colorCode;

    // 视频时间点 毫秒
    private Integer videoTimeMs;

    // 弹幕模式：0 滚动 1 顶部 2 底部
    private Byte mode;

    // 字号：0 小 1 标准
    private Byte fontSize;
}
