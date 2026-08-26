package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 看板娘跨会话长期记忆
@Data
@TableName("forum_mascot_memory")
public class ForumMascotMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String summary;

    private String factsJson;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Byte deleteState;
}
