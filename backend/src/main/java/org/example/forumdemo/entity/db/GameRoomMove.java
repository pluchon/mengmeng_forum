package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 游戏房间落子记录表，用于五子棋棋谱和录像回放
@Data
@TableName("game_room_move")
public class GameRoomMove {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 游戏编码
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 步号，从 1 开始
    private Integer moveNo;

    // 落子用户 ID
    private Long userId;

    // 行号
    @TableField("row_index")
    private Integer rowIndex;

    // 列号
    @TableField("col_index")
    private Integer colIndex;

    // 棋子颜色：1黑 2白
    private Integer chess;

    // 该步耗时毫秒
    private Long spentMs;

    // 是否删除：0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
