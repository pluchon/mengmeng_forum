package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 音乐氛围标签池。
 *
 * <p>在此之前候选集是一份静态配置，AI 审核补出来的新标签只写进歌曲自己的 mood_tags，
 * 筛选栏永远只有内置那几个。这张表把内置 / AI 补充 / 创作者创建收进同一个池子。
 */
@Data
@TableName("music_mood_tag")
public class MusicMoodTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** BUILTIN 内置 / AI 补充 / USER 创作者创建 */
    private String source;

    private Long createUserId;

    /** 被歌曲使用次数，筛选栏按此降序 */
    private Integer useCount;

    private Integer enabled;

    private Integer deleteState;

    private Date createTime;

    private Date updateTime;
}
