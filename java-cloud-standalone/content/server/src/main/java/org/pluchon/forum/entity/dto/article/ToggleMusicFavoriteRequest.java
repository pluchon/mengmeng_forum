package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 收藏/取消收藏曲库曲目
@Data
public class ToggleMusicFavoriteRequest {

    @NotBlank
    private String musicKey;

    private String title;

    private String artist;

    private String album;

    private String durationText;

    private String coverUrl;

    @NotBlank
    private String audioUrl;

    private String lrcUrl;
}
