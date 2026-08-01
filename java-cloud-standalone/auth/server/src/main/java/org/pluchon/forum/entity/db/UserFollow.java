package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者 */
    private Long followerId;

    /** 被关注者 */
    private Long followeeId;

    private Date createTime;
}
