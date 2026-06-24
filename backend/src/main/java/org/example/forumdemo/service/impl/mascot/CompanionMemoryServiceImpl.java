package org.example.forumdemo.service.impl.mascot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.ForumCompanionMessage;
import org.example.forumdemo.entity.db.ForumCompanionSession;
import org.example.forumdemo.entity.dto.mascot.MascotHistoryTurn;
import org.example.forumdemo.entity.vo.mascot.CompanionMessageVO;
import org.example.forumdemo.entity.vo.mascot.CompanionSessionVO;
import org.example.forumdemo.mapper.ForumCompanionMessageMapper;
import org.example.forumdemo.mapper.ForumCompanionSessionMapper;
import org.example.forumdemo.service.interfaces.mascot.CompanionMemoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
public class CompanionMemoryServiceImpl implements CompanionMemoryService {

    private static final int MAX_HISTORY_TURNS = 16;
    private static final int TITLE_MAX = 48;

    @Resource
    private ForumCompanionSessionMapper companionSessionMapper;

    @Resource
    private ForumCompanionMessageMapper companionMessageMapper;

    @Override
    public String normalizeSkill(String skill) {
        String s = skill != null ? skill.trim().toLowerCase(Locale.ROOT) : "chat";
        if (!List.of("writing", "help", "chat", "drawing").contains(s)) {
            return "chat";
        }
        return s;
    }

    @Override
    public Long ensureSession(Long userId, String skill, String sessionIdRaw) {
        String sk = normalizeSkill(skill);
        if (sessionIdRaw != null && !sessionIdRaw.isBlank()) {
            try {
                long sid = Long.parseLong(sessionIdRaw.trim());
                ForumCompanionSession existing = companionSessionMapper.selectOne(
                        Wrappers.lambdaQuery(ForumCompanionSession.class)
                                .eq(ForumCompanionSession::getId, sid)
                                .eq(ForumCompanionSession::getUserId, userId)
                                .eq(ForumCompanionSession::getSkill, sk)
                                .eq(ForumCompanionSession::getDeleteState, (byte) 0));
                if (existing != null) {
                    return existing.getId();
                }
            } catch (NumberFormatException ignored) {
                /* create new */
            }
        }
        ForumCompanionSession row = new ForumCompanionSession();
        row.setUserId(userId);
        row.setSkill(sk);
        row.setDeleteState((byte) 0);
        row.setCreateTime(new Date());
        row.setUpdateTime(new Date());
        companionSessionMapper.insert(row);
        return row.getId();
    }

    @Override
    public List<MascotHistoryTurn> loadHistoryTurns(Long sessionId, int maxTurns) {
        int limit = maxTurns > 0 ? maxTurns : MAX_HISTORY_TURNS;
        List<ForumCompanionMessage> rows = companionMessageMapper.selectPage(new Page<>(1, limit * 2, false),
                Wrappers.lambdaQuery(ForumCompanionMessage.class)
                        .eq(ForumCompanionMessage::getSessionId, sessionId)
                        .eq(ForumCompanionMessage::getDeleteState, (byte) 0)
                        .orderByDesc(ForumCompanionMessage::getId)).getRecords();
        List<MascotHistoryTurn> turns = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            ForumCompanionMessage m = rows.get(i);
            MascotHistoryTurn t = new MascotHistoryTurn();
            t.setRole(m.getRole());
            if ("image".equals(m.getMsgType()) && m.getImageUrl() != null) {
                t.setContent(m.getContent() != null ? m.getContent() : "[图片]");
            } else {
                t.setContent(m.getContent() != null ? m.getContent() : "");
            }
            turns.add(t);
        }
        return turns;
    }

    @Override
    public void appendTextMessage(Long sessionId, String role, String content) {
        appendTextMessage(sessionId, role, content, null);
    }

    @Override
    public void appendTextMessage(Long sessionId, String role, String content, String searchImageUrl) {
        if (sessionId == null || content == null || content.isBlank()) {
            return;
        }
        ForumCompanionMessage m = new ForumCompanionMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content.trim());
        m.setMsgType("text");
        String img = searchImageUrl != null ? searchImageUrl.trim() : "";
        if (!img.isBlank() && img.length() <= 1024 && img.startsWith("http")) {
            m.setImageUrl(img);
        }
        m.setDeleteState((byte) 0);
        m.setCreateTime(new Date());
        companionMessageMapper.insert(m);
        touchSession(sessionId, "user".equals(role) ? content : null);
    }

    @Override
    public void appendImageMessage(Long sessionId, String role, String imageUrl, String promptText) {
        if (sessionId == null || imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        ForumCompanionMessage m = new ForumCompanionMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(promptText != null ? promptText.trim() : "");
        m.setMsgType("image");
        m.setImageUrl(imageUrl);
        m.setDeleteState((byte) 0);
        m.setCreateTime(new Date());
        companionMessageMapper.insert(m);
        touchSession(sessionId, promptText);
    }

    private void touchSession(Long sessionId, String userTextForTitle) {
        ForumCompanionSession s = companionSessionMapper.selectById(sessionId);
        if (s == null) {
            return;
        }
        s.setUpdateTime(new Date());
        if ((s.getTitle() == null || s.getTitle().isBlank()) && userTextForTitle != null && !userTextForTitle.isBlank()) {
            String t = userTextForTitle.trim();
            s.setTitle(t.length() > TITLE_MAX ? t.substring(0, TITLE_MAX) + "…" : t);
        }
        companionSessionMapper.updateById(s);
    }

    @Override
    public List<CompanionSessionVO> listSessions(Long userId, String skill, int limit) {
        String sk = normalizeSkill(skill);
        int lim = limit > 0 ? Math.min(limit, 50) : 30;
        List<ForumCompanionSession> rows = companionSessionMapper.selectPage(new Page<>(1, lim, false),
                Wrappers.lambdaQuery(ForumCompanionSession.class)
                        .eq(ForumCompanionSession::getUserId, userId)
                        .eq(ForumCompanionSession::getSkill, sk)
                        .eq(ForumCompanionSession::getDeleteState, (byte) 0)
                        .orderByDesc(ForumCompanionSession::getUpdateTime)).getRecords();
        List<CompanionSessionVO> out = new ArrayList<>();
        for (ForumCompanionSession r : rows) {
            CompanionSessionVO v = new CompanionSessionVO();
            v.setId(r.getId());
            v.setSkill(r.getSkill());
            v.setTitle(r.getTitle());
            v.setUpdateTime(r.getUpdateTime());
            out.add(v);
        }
        return out;
    }

    @Override
    public List<CompanionMessageVO> listMessages(Long userId, Long sessionId) {
        ForumCompanionSession sess = companionSessionMapper.selectOne(
                Wrappers.lambdaQuery(ForumCompanionSession.class)
                        .eq(ForumCompanionSession::getId, sessionId)
                        .eq(ForumCompanionSession::getUserId, userId)
                        .eq(ForumCompanionSession::getDeleteState, (byte) 0));
        if (sess == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "会话不存在"));
        }
        List<ForumCompanionMessage> rows = companionMessageMapper.selectList(
                Wrappers.lambdaQuery(ForumCompanionMessage.class)
                        .eq(ForumCompanionMessage::getSessionId, sessionId)
                        .eq(ForumCompanionMessage::getDeleteState, (byte) 0)
                        .orderByAsc(ForumCompanionMessage::getId));
        List<CompanionMessageVO> out = new ArrayList<>();
        for (ForumCompanionMessage m : rows) {
            CompanionMessageVO v = new CompanionMessageVO();
            v.setRole(m.getRole());
            v.setAt(m.getCreateTime());
            if ("image".equals(m.getMsgType())) {
                v.setType("image");
                v.setUrl(m.getImageUrl());
                v.setContent(m.getContent());
            } else {
                v.setType("text");
                v.setContent(m.getContent());
                if (m.getImageUrl() != null && !m.getImageUrl().isBlank()) {
                    v.setSearchImageUrl(m.getImageUrl().trim());
                }
            }
            out.add(v);
        }
        return out;
    }
}
