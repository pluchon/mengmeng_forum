package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicParseResultVO;
import org.springframework.web.multipart.MultipartFile;

// 从音频文件内嵌标签解析歌名/封面/歌词等
public interface ArticleMusicParseService {

    MusicParseResultVO parse(MultipartFile audio);
}
