package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户昵称与简介异步审核申请
@Data
@TableName("user_profile_change_request")
public class UserProfileChangeRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fieldType;

    private String candidateContent;

    private String contentHash;

    private Byte reviewStatus;

    private Integer retryCount;

    private String reviewReason;

    private Date reviewedAt;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
