package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 游戏定义表，控制游戏中心展示哪些游戏
@Data
@TableName("game_definition")
public class GameDefinition {

    // 主键 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 游戏编码，如 gobang
    private String gameCode;

    // 游戏名称
    private String gameName;

    // 游戏封面图地址
    private String coverUrl;

    // 游戏状态：1启用 0停用
    private Byte status;

    // 排序值，越小越靠前
    private Integer sort;

    // 是否删除：0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
