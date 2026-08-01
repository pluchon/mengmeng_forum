package org.pluchon.forum.service.impl.mascot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ForumCompanionMessage;
import org.pluchon.forum.entity.db.ForumCompanionSession;
import org.pluchon.forum.entity.dto.mascot.MascotHistoryTurn;
import org.pluchon.forum.entity.vo.mascot.CompanionMessageVO;
import org.pluchon.forum.entity.vo.mascot.CompanionSessionVO;
import org.pluchon.forum.entity.vo.mascot.CompanionContextWindowVO;
import org.pluchon.forum.entity.vo.mascot.CompanionImageGalleryItemVO;
import org.pluchon.forum.mapper.ForumCompanionMessageMapper;
import org.pluchon.forum.mapper.ForumCompanionSessionMapper;
import org.pluchon.forum.service.interfaces.mascot.CompanionMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
public class CompanionMemoryServiceImpl implements CompanionMemoryService {

    private static final int MAX_HISTORY_TURNS = 16;
    private static final int TITLE_MAX = 48;
    private static final long CONTEXT_MAX_TOKENS = 128_000L;

    @Autowired
    private ForumCompanionSessionMapper companionSessionMapper;

    @Autowired
    private ForumCompanionMessageMapper companionMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String normalizeSkill(String skill) {
        String s = skill != null ? skill.trim().toLowerCase(Locale.ROOT) : "chat";
        if (!List.of("writing", "help", "chat", "drawing").contains(s)) {
            return "chat";
        }
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureSession(Long userId, String skill, String sessionIdRaw) {
        String sk = normalizeSkill(skill);
        if (sessionIdRaw != null && !sessionIdRaw.isBlank()) {
            try {
                long sid = Long.parseLong(sessionIdRaw.trim());
                ForumCompanionSession existing = companionSessionMapper.selectOne(
                        new LambdaQueryWrapper<ForumCompanionSession>()
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
        ForumCompanionMessage latestSummary = companionMessageMapper.selectOne(
                new LambdaQueryWrapper<ForumCompanionMessage>()
                        .eq(ForumCompanionMessage::getSessionId, sessionId)
                        .eq(ForumCompanionMessage::getMsgType, "context_summary")
                        .eq(ForumCompanionMessage::getDeleteState, (byte) 0)
                        .orderByDesc(ForumCompanionMessage::getId)
                        .last("LIMIT 1"));
        LambdaQueryWrapper<ForumCompanionMessage> query = new LambdaQueryWrapper<ForumCompanionMessage>()
                .eq(ForumCompanionMessage::getSessionId, sessionId)
                .eq(ForumCompanionMessage::getDeleteState, (byte) 0)
                .ne(ForumCompanionMessage::getMsgType, "context_summary")
                .orderByDesc(ForumCompanionMessage::getId);
        if (latestSummary != null) {
            query.gt(ForumCompanionMessage::getId, latestSummary.getId());
        }
        List<ForumCompanionMessage> rows = companionMessageMapper.selectPage(new Page<>(1, limit * 2, false),
                query).getRecords();
        List<MascotHistoryTurn> turns = new ArrayList<>();
        if (latestSummary != null && latestSummary.getContent() != null && !latestSummary.getContent().isBlank()) {
            MascotHistoryTurn summary = new MascotHistoryTurn();
            summary.setRole("assistant");
            summary.setContent("【已压缩的先前上下文】\n" + latestSummary.getContent().trim());
            turns.add(summary);
        }
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
    @Transactional(rollbackFor = Exception.class)
    public Long appendTextMessage(Long sessionId, String role, String content) {
        return appendTextMessage(sessionId, role, content, (String) null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long appendTextMessage(Long sessionId, String role, String content, String searchImageUrl) {
        List<CompanionImageGalleryItemVO> gallery = new ArrayList<>();
        if (searchImageUrl != null && !searchImageUrl.isBlank()) {
            CompanionImageGalleryItemVO item = new CompanionImageGalleryItemVO();
            item.setUrl(searchImageUrl.trim());
            gallery.add(item);
        }
        return appendTextMessage(sessionId, role, content, gallery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long appendTextMessage(Long sessionId, String role, String content,
                                  List<CompanionImageGalleryItemVO> imageGallery) {
        if (sessionId == null || content == null || content.isBlank()) {
            return null;
        }
        ForumCompanionMessage m = new ForumCompanionMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content.trim());
        m.setMsgType("text");
        List<CompanionImageGalleryItemVO> gallery = sanitizeImageGallery(imageGallery);
        if (!gallery.isEmpty()) {
            m.setImageUrl(gallery.get(0).getUrl());
            try {
                m.setMetadataJson(objectMapper.writeValueAsString(gallery));
            } catch (Exception ignored) {
                /* 图集持久化失败时，至少保留首图兼容旧会话。 */
            }
        }
        m.setDeleteState((byte) 0);
        m.setCreateTime(new Date());
        companionMessageMapper.insert(m);
        touchSession(sessionId, "user".equals(role) ? content : null);
        return m.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
                new LambdaQueryWrapper<ForumCompanionSession>()
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
                new LambdaQueryWrapper<ForumCompanionSession>()
                        .eq(ForumCompanionSession::getId, sessionId)
                        .eq(ForumCompanionSession::getUserId, userId)
                        .eq(ForumCompanionSession::getDeleteState, (byte) 0));
        if (sess == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "会话不存在"));
        }
        List<ForumCompanionMessage> rows = companionMessageMapper.selectList(
                new LambdaQueryWrapper<ForumCompanionMessage>()
                        .eq(ForumCompanionMessage::getSessionId, sessionId)
                        .eq(ForumCompanionMessage::getDeleteState, (byte) 0)
                        .orderByAsc(ForumCompanionMessage::getId));
        List<CompanionMessageVO> out = new ArrayList<>();
        for (ForumCompanionMessage m : rows) {
            CompanionMessageVO v = new CompanionMessageVO();
            v.setId(m.getId());
            v.setRole(m.getRole());
            v.setAt(m.getCreateTime());
            if ("image".equals(m.getMsgType())) {
                v.setType("image");
                v.setUrl(m.getImageUrl());
                v.setContent(m.getContent());
            } else if ("context_summary".equals(m.getMsgType())) {
                v.setType("context_summary");
                v.setContent("");
            } else {
                v.setType("text");
                v.setContent(m.getContent());
                List<CompanionImageGalleryItemVO> gallery = readImageGallery(m.getMetadataJson());
                if (!gallery.isEmpty()) {
                    v.setImageGallery(gallery);
                } else if (m.getImageUrl() != null && !m.getImageUrl().isBlank()) {
                    v.setSearchImageUrl(m.getImageUrl().trim());
                }
            }
            out.add(v);
        }
        return out;
    }

    @Override
    public CompanionContextWindowVO getContextWindow(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        long used = estimateTokens(loadCompressibleHistory(userId, sessionId));
        CompanionContextWindowVO vo = new CompanionContextWindowVO();
        vo.setUsedTokens(used);
        vo.setMaxTokens(CONTEXT_MAX_TOKENS);
        vo.setCanCompress(used > 0);
        return vo;
    }

    @Override
    public List<MascotHistoryTurn> loadCompressibleHistory(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        List<ForumCompanionMessage> rows = companionMessageMapper.selectList(
                new LambdaQueryWrapper<ForumCompanionMessage>()
                        .eq(ForumCompanionMessage::getSessionId, sessionId)
                        .eq(ForumCompanionMessage::getDeleteState, (byte) 0)
                        .ne(ForumCompanionMessage::getMsgType, "context_summary")
                        .orderByAsc(ForumCompanionMessage::getId));
        List<MascotHistoryTurn> out = new ArrayList<>();
        for (ForumCompanionMessage row : rows) {
            if (row.getContent() == null || row.getContent().isBlank()) {
                continue;
            }
            MascotHistoryTurn turn = new MascotHistoryTurn();
            turn.setRole("user".equals(row.getRole()) ? "user" : "assistant");
            turn.setContent(row.getContent().trim());
            out.add(turn);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendContextSummary(Long userId, Long sessionId, String summary, long sourceTokens) {
        requireOwnedSession(userId, sessionId);
        if (summary == null || summary.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ForumCompanionMessage row = new ForumCompanionMessage();
        row.setSessionId(sessionId);
        row.setRole("system");
        row.setContent(summary.trim());
        row.setMsgType("context_summary");
        row.setDeleteState((byte) 0);
        row.setCreateTime(new Date());
        row.setMetadataJson("{\"sourceTokens\":" + Math.max(0L, sourceTokens) + "}");
        companionMessageMapper.insert(row);
        touchSession(sessionId, null);
    }

    private void requireOwnedSession(Long userId, Long sessionId) {
        ForumCompanionSession session = companionSessionMapper.selectOne(
                new LambdaQueryWrapper<ForumCompanionSession>()
                        .eq(ForumCompanionSession::getId, sessionId)
                        .eq(ForumCompanionSession::getUserId, userId)
                        .eq(ForumCompanionSession::getDeleteState, (byte) 0));
        if (session == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "会话不存在"));
        }
    }

    private List<CompanionImageGalleryItemVO> sanitizeImageGallery(List<CompanionImageGalleryItemVO> raw) {
        List<CompanionImageGalleryItemVO> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (CompanionImageGalleryItemVO item : raw) {
            if (item == null || item.getUrl() == null) {
                continue;
            }
            String url = item.getUrl().trim();
            if (!url.startsWith("https://") || url.length() > 2048 || out.stream().anyMatch(x -> url.equals(x.getUrl()))) {
                continue;
            }
            CompanionImageGalleryItemVO accepted = new CompanionImageGalleryItemVO();
            accepted.setUrl(url);
            accepted.setTitle(item.getTitle() == null ? "" : item.getTitle().trim().substring(0, Math.min(120, item.getTitle().trim().length())));
            accepted.setSource(item.getSource() == null ? "" : item.getSource().trim().substring(0, Math.min(160, item.getSource().trim().length())));
            out.add(accepted);
            if (out.size() >= 5) {
                break;
            }
        }
        return out;
    }

    private List<CompanionImageGalleryItemVO> readImageGallery(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return sanitizeImageGallery(objectMapper.readValue(raw, new TypeReference<List<CompanionImageGalleryItemVO>>() { }));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private long estimateTokens(List<MascotHistoryTurn> turns) {
        long chars = 0L;
        for (MascotHistoryTurn turn : turns) {
            chars += turn.getContent() == null ? 0 : turn.getContent().length();
        }
        return Math.max(0L, (long) Math.ceil(chars * 0.8D));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameSession(Long userId, Long sessionId, String title) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (userId == null || userId <= 0 || sessionId == null || sessionId <= 0
                || normalizedTitle.isBlank() || normalizedTitle.length() > TITLE_MAX) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int updated = companionSessionMapper.update(null, new LambdaUpdateWrapper<ForumCompanionSession>()
                .eq(ForumCompanionSession::getId, sessionId)
                .eq(ForumCompanionSession::getUserId, userId)
                .eq(ForumCompanionSession::getDeleteState, (byte) 0)
                .set(ForumCompanionSession::getTitle, normalizedTitle)
                .set(ForumCompanionSession::getUpdateTime, new Date()));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "会话不存在"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId) {
        if (userId == null || userId <= 0 || sessionId == null || sessionId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ForumCompanionSession session = companionSessionMapper.selectOne(
                new LambdaQueryWrapper<ForumCompanionSession>()
                        .eq(ForumCompanionSession::getId, sessionId)
                        .eq(ForumCompanionSession::getUserId, userId)
                        .eq(ForumCompanionSession::getDeleteState, (byte) 0));
        if (session == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "会话不存在"));
        }
        companionMessageMapper.update(null, new LambdaUpdateWrapper<ForumCompanionMessage>()
                .eq(ForumCompanionMessage::getSessionId, sessionId)
                .eq(ForumCompanionMessage::getDeleteState, (byte) 0)
                .set(ForumCompanionMessage::getDeleteState, (byte) 1));
        companionSessionMapper.update(null, new LambdaUpdateWrapper<ForumCompanionSession>()
                .eq(ForumCompanionSession::getId, sessionId)
                .eq(ForumCompanionSession::getUserId, userId)
                .eq(ForumCompanionSession::getDeleteState, (byte) 0)
                .set(ForumCompanionSession::getDeleteState, (byte) 1)
                .set(ForumCompanionSession::getUpdateTime, new Date()));
    }
}
