package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户看板娘偏好，对应 user_mascot_preference（ai 权威）
@Data
@TableName("user_mascot_preference")
public class UserMascotPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long mascotModelId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
