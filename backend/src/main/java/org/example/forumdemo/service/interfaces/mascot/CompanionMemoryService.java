package org.example.forumdemo.service.interfaces.mascot;

import org.example.forumdemo.entity.dto.mascot.MascotHistoryTurn;
import org.example.forumdemo.entity.vo.mascot.CompanionMessageVO;
import org.example.forumdemo.entity.vo.mascot.CompanionSessionVO;

import java.util.List;

public interface CompanionMemoryService {

    String normalizeSkill(String skill);

    /** 解析或创建会话，返回 DB 会话 id 字符串 */
    Long ensureSession(Long userId, String skill, String sessionIdRaw);

    List<MascotHistoryTurn> loadHistoryTurns(Long sessionId, int maxTurns);

    void appendTextMessage(Long sessionId, String role, String content);

    /** 助手文本消息；searchImageUrl 为联网检索配图（存 image_url，msg_type 仍为 text） */
    void appendTextMessage(Long sessionId, String role, String content, String searchImageUrl);

    void appendImageMessage(Long sessionId, String role, String imageUrl, String promptText);

    List<CompanionSessionVO> listSessions(Long userId, String skill, int limit);

    List<CompanionMessageVO> listMessages(Long userId, Long sessionId);

    void renameSession(Long userId, Long sessionId, String title);

    void deleteSession(Long userId, Long sessionId);
}
