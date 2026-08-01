package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户推荐画像快照，不保存原始互动正文
@Data
@TableName("forum_user_ai_profile_snapshot")
public class ForumUserAiProfileSnapshot {

    // 主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 画像递增版本
    private Long profileVersion;

    // 结构化画像 JSON
    private String profileJson;

    // 特征协议版本
    private String featureVersion;

    // 信号窗口开始时间
    private Date sourceWindowStart;

    // 信号窗口结束时间
    private Date sourceWindowEnd;

    // 下次允许自动刷新时间
    private Date refreshAfter;

    // 画像生成来源
    private String generatedBy;

    // 逻辑删除状态
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
