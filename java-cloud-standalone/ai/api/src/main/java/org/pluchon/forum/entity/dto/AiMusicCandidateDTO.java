package org.pluchon.forum.entity.dto;

import lombok.Data;

// 配乐 AI 候选曲目
@Data
public class AiMusicCandidateDTO {

    private String musicKey;

    private String name;

    private String title;

    private String artist;

    private String album;

    /** 氛围标签。描述性 query（如「适合深夜听的伤感歌」）唯一能比对的字段 */
    private java.util.List<String> moodTags;
}
