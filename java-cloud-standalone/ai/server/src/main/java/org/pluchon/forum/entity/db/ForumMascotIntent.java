package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 看板娘牵线的一条意愿。
 *
 * <p>与 {@link ForumMascotMemory} 刻意分开：记忆是「你是谁」（一人一行、覆盖式、无期限、
 * 只喂给你自己的对话），意愿是「你现在想要什么」（一人多行、会过期、将来可能被匹配给
 * 另一个人看）。混在一起，记忆就会被匹配逻辑读出去。
 */
@Data
@TableName("forum_mascot_intent")
public class ForumMascotIntent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** seek=想找人 offer=能帮人 */
    private String intentKind;

    /** 用户在卡片上点过头的那句话；没点头的一律不会到这里 */
    private String intentText;

    /** 来自哪个看板娘会话；用于「同一会话不重复问」 */
    private Long sourceSessionId;

    /** ACTIVE | MATCHED | EXPIRED | CANCELLED */
    private String state;

    private Date expireAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Byte deleteState;
}
