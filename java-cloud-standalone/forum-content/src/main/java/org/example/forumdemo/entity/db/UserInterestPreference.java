package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户细分板块兴趣与个性化开关
@Data
@TableName("user_interest_preference")
public class UserInterestPreference {

    // 主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 板块ID，0 表示该用户的个性化开关记录
    private Long boardId;

    // 个性化状态，见 PersonalizationState
    private Byte personalizedEnabled;

    // 逻辑删除状态
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
