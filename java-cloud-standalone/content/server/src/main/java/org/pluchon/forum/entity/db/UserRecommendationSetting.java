package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户推荐显示设置
@Data
@TableName("user_recommendation_setting")
public class UserRecommendationSetting {

    // 主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 是否启用个性化推荐
    private Byte personalizedEnabled;

    // 手选兴趣版块 ID JSON 数组
    private String interestBoardIds;

    // 逻辑删除状态
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
