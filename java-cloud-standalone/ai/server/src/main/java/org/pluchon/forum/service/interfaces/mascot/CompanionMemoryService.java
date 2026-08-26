package org.pluchon.forum.service.interfaces.mascot;

import org.pluchon.forum.entity.dto.MascotHistoryTurn;
import org.pluchon.forum.entity.vo.CompanionMessageVO;
import org.pluchon.forum.entity.vo.CompanionSessionVO;
import org.pluchon.forum.entity.vo.CompanionContextWindowVO;
import org.pluchon.forum.entity.vo.CompanionImageGalleryItemVO;
import org.pluchon.forum.entity.vo.MascotMemoryVO;

import java.util.List;

public interface CompanionMemoryService {

    String normalizeSkill(String skill);

    // 解析或创建会话，返回 DB 会话 id 字符串
    Long ensureSession(Long userId, String skill, String sessionIdRaw);

    List<MascotHistoryTurn> loadHistoryTurns(Long sessionId, int maxTurns);

    Long appendTextMessage(Long sessionId, String role, String content);

    // 助手文本消息；searchImageUrl 为联网检索配图 存 image_url，msg_type 仍为 text
    Long appendTextMessage(Long sessionId, String role, String content, String searchImageUrl);

    Long appendTextMessage(Long sessionId, String role, String content,
                           List<CompanionImageGalleryItemVO> imageGallery);

    void appendImageMessage(Long sessionId, String role, String imageUrl, String promptText);

    List<CompanionSessionVO> listSessions(Long userId, String skill, int limit);

    List<CompanionMessageVO> listMessages(Long userId, Long sessionId);

    CompanionContextWindowVO getContextWindow(Long userId, Long sessionId);

    List<MascotHistoryTurn> loadCompressibleHistory(Long userId, Long sessionId);

    void appendContextSummary(Long userId, Long sessionId, String summary, long sourceTokens);

    MascotMemoryVO getMascotMemory(Long userId);

    void saveMascotMemory(Long userId, String summary, List<String> facts);

    void renameSession(Long userId, Long sessionId, String title);

    void deleteSession(Long userId, Long sessionId);
}
