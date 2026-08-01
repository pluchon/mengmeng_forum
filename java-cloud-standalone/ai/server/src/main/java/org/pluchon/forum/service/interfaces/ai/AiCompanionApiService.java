package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.entity.dto.ai.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.ai.AiImageRequest;
import org.pluchon.forum.entity.dto.ai.AiPolishRequest;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.ai.AiPriceEstimateVO;
import org.pluchon.forum.entity.vo.ai.AiPolishResponseVO;

// AI 写作/生图对外用例编排（配额、计费、转存）
public interface AiCompanionApiService {

    AiPriceEstimateVO priceEstimate(Long userId, String skill, String route, String quality);

    AiPolishResponseVO polish(Long userId, AiPolishRequest request);

    AiHubCoverHintsResultVO coverHints(Long userId, AiCoverHintsRequest request);

    AiImageResponseVO image(Long userId, AiImageRequest request);
}
