package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 积分钱包实体，对应 points_wallet economy 权威，替代 user.points
@Data
@TableName("points_wallet")
public class PointsWallet {

    // 主键，自增
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID 逻辑关联 auth.user
    private Long userId;

    // 当前积分余额
    private Integer balance;

    // 并发写路径使用 selectByUserIdForUpdate + 原子加减，不用 MyBatis-Plus @Version
    private Integer version;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
