package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户在单个游戏下的资料与战绩摘要
@Data
@TableName("game_user_profile")
public class GameUserProfile {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户 ID，对应 user.id
    private Long userId;

    // 游戏编码，如 gobang
    private String gameCode;

    // 游戏天梯积分，与论坛积分隔离
    private Integer score;

    // 总对局数
    private Integer totalCount;

    // 胜局数
    private Integer winCount;

    // 负局数
    private Integer loseCount;

    // 平局数，一期暂不产生，预留
    private Integer drawCount;

    // 当前状态：IDLE / MATCHING / PLAYING
    private String currentStatus;

    // 当前房间 ID，非对局中为空
    private String currentRoomId;

    // 是否删除：0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
