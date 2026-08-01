package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.entity.db.GameGobangMatchRecord;
import org.pluchon.forum.entity.db.GameJinziMatchRecord;
import org.pluchon.forum.entity.db.GameSettlementEvent;
import org.pluchon.forum.entity.vo.mq.GameFinishedMqVO;
import org.pluchon.forum.mapper.GameGobangMatchRecordMapper;
import org.pluchon.forum.mapper.GameJinziMatchRecordMapper;
import org.pluchon.forum.mapper.GameSettlementEventMapper;
import org.pluchon.forum.service.interfaces.game.GameMqEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

// 游戏 MQ 事件服务，当前先完成幂等消费骨架，后续承接通知、统计和排行榜刷新
@Slf4j
@Service
public class GameMqEventServiceImpl implements GameMqEventService {

    private static final int MAX_RETRY_COUNT = 5;

    @Autowired
    private GameSettlementEventMapper gameSettlementEventMapper;

    @Autowired
    private GameGobangMatchRecordMapper gameGobangMatchRecordMapper;

    @Autowired
    private GameJinziMatchRecordMapper gameJinziMatchRecordMapper;

    @Autowired
    private ForumProducer forumProducer;

    @Override
    public void handleGameFinished(GameFinishedMqVO event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            log.warn("[游戏MQ] 对局结束事件缺少 eventId，已忽略");
            return;
        }
        LambdaUpdateWrapper<GameSettlementEvent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GameSettlementEvent::getEventId, event.getEventId())
                .ne(GameSettlementEvent::getStatus, GameConstants.SETTLEMENT_EVENT_CONSUMED)
                .set(GameSettlementEvent::getStatus, GameConstants.SETTLEMENT_EVENT_CONSUMED)
                .set(GameSettlementEvent::getLastError, null);
        int updated = gameSettlementEventMapper.update(null, wrapper);
        if (updated > 0) {
            log.info("[游戏MQ] 对局结束事件已消费 gameCode={}, roomId={}, eventId={}",
                    event.getGameCode(),
                    event.getRoomId(),
                    event.getEventId());
        } else {
            log.debug("[游戏MQ] 对局结束事件重复消费或事件不存在 eventId={}", event.getEventId());
        }
    }

    // 定时补偿结算后 MQ 投递失败的事件，避免通知、统计和榜单刷新永久丢失
    @Scheduled(fixedDelay = 30_000)
    public void retryPendingGameEvents() {
        List<GameSettlementEvent> events = gameSettlementEventMapper.selectList(
                new LambdaQueryWrapper<GameSettlementEvent>()
                        .eq(GameSettlementEvent::getDeleteState, (byte) 0)
                        .in(GameSettlementEvent::getStatus,
                                List.of(GameConstants.SETTLEMENT_EVENT_CREATED, GameConstants.SETTLEMENT_EVENT_MQ_PENDING))
        );
        for (GameSettlementEvent event : events) {
            retryGameFinishedEvent(event);
        }
    }

    private void retryGameFinishedEvent(GameSettlementEvent event) {
        if (event == null
                || !GameConstants.SETTLEMENT_EVENT_GAME_FINISHED.equals(event.getEventType())) {
            return;
        }
        int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        if (retryCount >= MAX_RETRY_COUNT) {
            markDead(event, "超过最大重试次数");
            return;
        }
        Long winnerUserId = null;
        Long loserUserId = null;
        String endReason = null;
        Long endedAtMs = System.currentTimeMillis();
        if (event.getRecordId() != null) {
            if (GameConstants.GOBANG.equals(event.getGameCode())) {
                GameGobangMatchRecord record = gameGobangMatchRecordMapper.selectById(event.getRecordId());
                if (record == null) {
                    markDead(event, "五子棋对局记录不存在");
                    return;
                }
                winnerUserId = record.getWinnerUserId();
                loserUserId = record.getLoserUserId();
                endReason = record.getEndReason();
                endedAtMs = record.getEndedAt() == null ? endedAtMs : record.getEndedAt().getTime();
            } else if (GameConstants.JINZI.equals(event.getGameCode())) {
                GameJinziMatchRecord record = gameJinziMatchRecordMapper.selectById(event.getRecordId());
                if (record == null) {
                    markDead(event, "井字棋对局记录不存在");
                    return;
                }
                winnerUserId = record.getWinnerUserId();
                loserUserId = record.getLoserUserId();
                endReason = record.getEndReason();
                endedAtMs = record.getEndedAt() == null ? endedAtMs : record.getEndedAt().getTime();
            } else {
                markDead(event, "不支持的游戏编码");
                return;
            }
        } else {
            markDead(event, "对局记录不存在");
            return;
        }
        try {
            forumProducer.sendGameFinished(new GameFinishedMqVO(
                    event.getEventId(),
                    event.getGameCode(),
                    event.getRoomId(),
                    event.getRecordId(),
                    winnerUserId,
                    loserUserId,
                    endReason,
                    null,
                    endedAtMs
            ));
            LambdaUpdateWrapper<GameSettlementEvent> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(GameSettlementEvent::getEventId, event.getEventId())
                    .in(GameSettlementEvent::getStatus,
                            List.of(GameConstants.SETTLEMENT_EVENT_CREATED, GameConstants.SETTLEMENT_EVENT_MQ_PENDING))
                    .set(GameSettlementEvent::getStatus, GameConstants.SETTLEMENT_EVENT_MQ_SENT)
                    .set(GameSettlementEvent::getLastError, null);
            gameSettlementEventMapper.update(null, wrapper);
        } catch (Exception e) {
            LambdaUpdateWrapper<GameSettlementEvent> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(GameSettlementEvent::getEventId, event.getEventId())
                    .set(GameSettlementEvent::getStatus, GameConstants.SETTLEMENT_EVENT_MQ_PENDING)
                    .set(GameSettlementEvent::getRetryCount, retryCount + 1)
                    .set(GameSettlementEvent::getLastError, truncateError(e.getMessage()));
            gameSettlementEventMapper.update(null, wrapper);
            log.warn("[游戏MQ] 结算事件补偿投递失败 eventId={}, retry={}, error={}",
                    event.getEventId(),
                    retryCount + 1,
                    e.getMessage());
        }
    }

    private void markDead(GameSettlementEvent event, String reason) {
        LambdaUpdateWrapper<GameSettlementEvent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GameSettlementEvent::getEventId, event.getEventId())
                .set(GameSettlementEvent::getStatus, GameConstants.SETTLEMENT_EVENT_DEAD)
                .set(GameSettlementEvent::getLastError, truncateError(reason));
        gameSettlementEventMapper.update(null, wrapper);
        log.warn("[游戏MQ] 结算事件进入 DEAD eventId={}, reason={}", event.getEventId(), reason);
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
