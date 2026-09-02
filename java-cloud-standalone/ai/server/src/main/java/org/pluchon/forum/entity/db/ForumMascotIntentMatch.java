package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 一次牵线。
 *
 * <p>在双方都点头之前，任何一方都看不到对方是谁——邀约里只有 reason。
 * 任何一方 DECLINED，另一方永远不会知道这次匹配发生过。
 */
@Data
@TableName("forum_mascot_intent_match")
public class ForumMascotIntentMatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long intentAId;

    private Long intentBId;

    private Long userAId;

    private Long userBId;

    /** 交集描述；双方看到的是同一句，不含任何身份信息 */
    private String reason;

    /** PENDING | ACCEPTED | DECLINED */
    private String aState;

    private String bState;

    /** PENDING | CONNECTED | CLOSED */
    private String state;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Byte deleteState;
}
