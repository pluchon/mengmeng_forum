package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 五子棋房间落子记录，用于棋谱和录像回放
@Data
@TableName("game_gobang_room_move")
public class GameGobangRoomMove {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roomId;

    private Integer moveNo;

    private Long userId;

    @TableField("row_index")
    private Integer rowIndex;

    @TableField("col_index")
    private Integer colIndex;

    private Integer chess;

    private Long spentMs;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
