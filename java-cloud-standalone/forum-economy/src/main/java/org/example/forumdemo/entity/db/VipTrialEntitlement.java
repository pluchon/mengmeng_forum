package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

// TRIAL_900 体验会员权益
@Data
@TableName("vip_trial_entitlement")
public class VipTrialEntitlement {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String trialCode;
    private String status;
    private Date expireAt;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
