package org.example.forumdemo.service.interfaces.ai;

import org.example.forumdemo.entity.dto.ai.AiCoverHintsRequest;
import org.example.forumdemo.entity.dto.ai.AiImageRequest;
import org.example.forumdemo.entity.dto.ai.AiWriteRequest;
import org.example.forumdemo.entity.dto.ai.RagArticleIndexDTO;
import org.example.forumdemo.entity.dto.ai.RagUserIndexDTO;
import org.example.forumdemo.entity.vo.ai.AiHubCoverHintsResultVO;
import org.example.forumdemo.entity.vo.ai.AiHubImageResultVO;
import org.example.forumdemo.entity.vo.ai.AiHubWriteResultVO;
import org.example.forumdemo.entity.vo.ai.RagArticleVectorHitVO;
import org.example.forumdemo.entity.vo.ai.RagUserVectorHitVO;

import java.util.List;
import java.util.Map;

public interface AiHubService {

    AiHubWriteResultVO write(Long userId, AiWriteRequest request);

    AiHubCoverHintsResultVO coverHints(Long userId, AiCoverHintsRequest request);

    AiHubImageResultVO image(Long userId, AiImageRequest request);

    void indexArticleRag(RagArticleIndexDTO payload);

    void indexUserRag(RagUserIndexDTO payload);

    List<Long> ragVectorSearchArticles(String query, List<Map<String, Object>> candidates);

    List<RagArticleVectorHitVO> ragArticleVectorRanked(String query, List<Map<String, Object>> candidates);

    List<Long> ragVectorSearchUsers(String query, List<Map<String, Object>> candidates);

    List<RagUserVectorHitVO> ragUserVectorRanked(String query);
}
