package org.pluchon.forum.service.remote;

import org.pluchon.forum.content.client.ContentAiHubInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// 内容服务访问 AI 网关的本地适配
@Service
public class ContentAiGatewayService {

    @Autowired
    private ContentAiHubInternalFeignClient contentAiHubInternalFeignClient;

    public String validateText(String content) {
        return contentAiHubInternalFeignClient.validateText(content);
    }

    public boolean validateImage(MultipartFile file) {
        return Boolean.TRUE.equals(contentAiHubInternalFeignClient.validateImage(file));
    }

    public String summarize(String content) {
        return contentAiHubInternalFeignClient.summarize(content);
    }

    public List<Long> rankSemanticCandidates(String query, List<Map<String, Object>> candidates) {
        org.pluchon.forum.api.ai.AiRagSearchRequest request = new org.pluchon.forum.api.ai.AiRagSearchRequest();
        request.setQuery(query);
        request.setCandidates(candidates);
        return contentAiHubInternalFeignClient.rankSemanticCandidates(request);
    }
}
