package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicTrimResultVO;
import org.springframework.web.multipart.MultipartFile;

// 音频裁剪（FFmpeg 微服务）
public interface ArticleMusicTrimService {

    MusicTrimResultVO trim(MultipartFile audio, double startSec, double endSec);
}
