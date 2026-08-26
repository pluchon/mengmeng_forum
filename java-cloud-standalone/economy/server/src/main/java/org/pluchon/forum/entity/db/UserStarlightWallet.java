package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 萌星辉钱包，对应 user_starlight_wallet
@Data
@TableName("user_starlight_wallet")
public class UserStarlightWallet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    // 萌星辉余额
    private Integer balance;

    // 并发写路径使用行锁 + 原子加减，不用 MyBatis-Plus @Version
    private Integer version;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
