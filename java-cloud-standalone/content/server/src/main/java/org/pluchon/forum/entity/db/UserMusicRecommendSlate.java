package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户音乐大厅推荐片单
@Data
@TableName("user_music_recommend_slate")
public class UserMusicRecommendSlate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String periodKey;

    private String source;

    private String musicKeysJson;

    private Date expireTime;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
