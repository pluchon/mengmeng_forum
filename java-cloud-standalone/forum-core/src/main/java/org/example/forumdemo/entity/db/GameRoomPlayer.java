package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 游戏房间玩家映射表，用于观战、复盘和房间历史查询
@Data
@TableName("game_room_player")
public class GameRoomPlayer {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 游戏编码
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 用户 ID
    private Long userId;

    // 房间角色：BLACK/WHITE/SPECTATOR/AI
    private String roomRole;

    // 是否删除：0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
