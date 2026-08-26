package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 绑定帖子配乐
@Data
public class SetArticleMusicRequest {

    @NotNull
    private Long articleId;

    @NotBlank
    private String musicKey;

    @NotBlank
    private String musicTitle;

    private String musicCoverUrl;

    @NotBlank
    private String musicAudioUrl;

    private String musicLrcUrl;
}
