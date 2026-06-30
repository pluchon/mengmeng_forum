package org.example.forumdemo.entity.vo.driftbottle;

import lombok.Data;

import java.util.Date;

// 漂流瓶列表项响应
@Data
public class DriftBottleListItemVO {

    // 漂流瓶 ID
    private Long id;

    // 瓶子内容
    private String content;

    // 心情标签
    private String moodType;

    // 状态文本
    private String statusText;

    // 评论数量
    private Integer commentCount;

    // 被捞次数
    private Integer pickedCount;

    // 最近一条匿名评论
    private String latestComment;

    // 创建时间
    private Date createTime;
}
