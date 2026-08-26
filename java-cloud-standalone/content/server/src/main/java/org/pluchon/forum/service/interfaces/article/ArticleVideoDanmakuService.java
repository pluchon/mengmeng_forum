package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.dto.article.SendDanmakuRequest;
import org.pluchon.forum.entity.vo.article.DanmakuItemVO;

import java.util.List;

public interface ArticleVideoDanmakuService {

    DanmakuItemVO sendDanmaku(SendDanmakuRequest req, Long loginUserId);

    List<DanmakuItemVO> listByTimeWindow(Long articleId, Integer fromMs, Integer toMs, Long loginUserId);
}
