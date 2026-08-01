package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.dto.article.SendDanmakuRequest;
import org.example.forumdemo.entity.vo.article.DanmakuItemVO;

import java.util.List;

public interface ArticleVideoDanmakuService {

    DanmakuItemVO sendDanmaku(SendDanmakuRequest req, Long loginUserId);

    List<DanmakuItemVO> listByTimeWindow(Long articleId, Integer fromMs, Integer toMs);
}
