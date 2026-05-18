package org.example.forumdemo.service.interfaces.ai;

import org.example.forumdemo.entity.dto.ai.AiCoverHintsRequest;
import org.example.forumdemo.entity.dto.ai.AiImageRequest;
import org.example.forumdemo.entity.dto.ai.AiWriteRequest;

import java.util.Map;

public interface AiHubService {

    Map<String, Object> write(Long userId, AiWriteRequest request);

    Map<String, Object> coverHints(Long userId, AiCoverHintsRequest request);

    Map<String, Object> image(Long userId, AiImageRequest request);
}
