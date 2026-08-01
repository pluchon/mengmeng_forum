package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 漂流瓶打捞记录表
@Data
@TableName("drift_bottle_pick_log")
public class DriftBottlePickLog {

    // 记录 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 漂流瓶 ID
    private Long bottleId;

    // 打捞用户 ID
    private Long userId;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 是否删除: 0否 1是
    private Byte deleteState;
}
