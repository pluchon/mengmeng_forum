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
}
