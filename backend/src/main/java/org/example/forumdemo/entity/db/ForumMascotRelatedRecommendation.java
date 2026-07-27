package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 看板娘已确认的相关帖子检索
@Data
@TableName("forum_mascot_related_recommendation")
public class ForumMascotRelatedRecommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("companion_session_id")
    private Long companionSessionId;

    private String query;

    @TableField("result_state")
    private String resultState;

    @TableField("result_count")
    private Integer resultCount;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
