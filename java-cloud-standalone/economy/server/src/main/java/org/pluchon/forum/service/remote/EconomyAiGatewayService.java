package org.pluchon.forum.service.remote;

import org.pluchon.forum.economy.client.EconomyAiHubInternalFeignClient;
import org.pluchon.forum.entity.dto.RagEmojiIndexDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// 经济服务访问 AI 网关的本地适配
@Service
public class EconomyAiGatewayService {

    @Autowired
    private EconomyAiHubInternalFeignClient economyAiHubInternalFeignClient;

    public String validateText(String content) {
        return economyAiHubInternalFeignClient.validateText(content);
    }

    public List<Long> rankSemanticCandidates(String query, List<Map<String, Object>> candidates) {
        org.pluchon.forum.api.ai.AiRagSearchRequest request = new org.pluchon.forum.api.ai.AiRagSearchRequest();
        request.setQuery(query);
        request.setCandidates(candidates);
        return economyAiHubInternalFeignClient.rankSemanticCandidates(request);
    }

    public void indexEmojiRag(RagEmojiIndexDTO payload) {
        economyAiHubInternalFeignClient.indexEmojiRag(payload);
    }

    public List<Long> ragVectorSearchEmojis(String query) {
        org.pluchon.forum.api.ai.AiRagSearchRequest request = new org.pluchon.forum.api.ai.AiRagSearchRequest();
        request.setQuery(query);
        try {
            return economyAiHubInternalFeignClient.ragVectorSearchEmojis(request);
        } catch (RuntimeException e) {
            return java.util.Collections.emptyList();
        }
    }
}
