package org.pluchon.forum.entity.dto;

import lombok.Data;

import java.util.List;

// RAG 曲目向量索引请求
@Data
public class RagMusicIndexDTO {

    private String musicKey;

    private String title;

    private String artist;

    private String genre;

    private List<String> moodTags;

    private String aiProfile;
}
