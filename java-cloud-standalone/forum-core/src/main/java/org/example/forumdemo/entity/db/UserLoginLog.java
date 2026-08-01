package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_login_log")
@Schema(description = "用户登录日志")
public class UserLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String loginType;

    private String ipAddress;

    private String userAgent;

    private Byte loginStatus;

    @JsonIgnore
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
