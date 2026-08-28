package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.entity.dto.AiArticleCoverRequest;
import org.pluchon.forum.entity.dto.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.AiImageRequest;
import org.pluchon.forum.entity.dto.AiPolishRequest;
import org.pluchon.forum.entity.vo.ai.AiArticleCoverResponseVO;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.ai.AiPolishResponseVO;

// AI 写作/生图对外用例编排 配额、计费、转存
public interface AiCompanionApiService {

    AiPolishResponseVO polish(Long userId, AiPolishRequest request);

    AiArticleCoverResponseVO articleCover(Long userId, AiArticleCoverRequest request);

    AiHubCoverHintsResultVO coverHints(Long userId, AiCoverHintsRequest request);

    AiImageResponseVO image(Long userId, AiImageRequest request);
}
