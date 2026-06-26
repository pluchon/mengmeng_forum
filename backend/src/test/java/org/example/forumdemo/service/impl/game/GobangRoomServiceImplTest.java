package org.example.forumdemo.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.entity.dto.game.GobangChatRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;

class GobangRoomServiceImplTest {

    private GobangRoomServiceImpl service;

    private GameConnectionRegistry gameConnectionRegistry;

    @BeforeEach
    void setUp() {
        service = new GobangRoomServiceImpl();
        gameConnectionRegistry = Mockito.mock(GameConnectionRegistry.class);
        ReflectionTestUtils.setField(service, "gameConnectionRegistry", gameConnectionRegistry);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldRejectChatWhenFinishedRoomAlreadyCleaned() {
        GobangChatRequest request = textChat("对局结束后发一句");

        Assertions.assertDoesNotThrow(() -> service.chat("cleaned-room", 1L, request, "req-chat"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(gameConnectionRegistry).sendToRoom(
                Mockito.eq("cleaned-room"),
                Mockito.eq(1L),
                payloadCaptor.capture()
        );
        Assertions.assertTrue(payloadCaptor.getValue().contains("当前对战已经结束，不能发送消息或表情包"));
        Mockito.verify(gameConnectionRegistry, Mockito.never()).broadcastRoom(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void shouldRejectChatWhenRoomStatusFinished() {
        GobangRoom room = new GobangRoom(1L, 2L);
        room.setRoomStatus(GameConstants.ROOM_FINISHED);
        rooms().put(room.getRoomId(), room);
        GobangChatRequest request = textChat("对局结束后再发一句");

        service.chat(room.getRoomId(), 1L, request, "req-chat");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(gameConnectionRegistry).sendToRoom(
                Mockito.eq(room.getRoomId()),
                Mockito.eq(1L),
                payloadCaptor.capture()
        );
        Assertions.assertTrue(payloadCaptor.getValue().contains("当前对战已经结束，不能发送消息或表情包"));
        Mockito.verify(gameConnectionRegistry, Mockito.never()).broadcastRoom(Mockito.anyString(), Mockito.anyString());
    }

    private GobangChatRequest textChat(String content) {
        GobangChatRequest request = new GobangChatRequest();
        request.setMessageType("TEXT");
        request.setContent(content);
        return request;
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, GobangRoom> rooms() {
        Object value = ReflectionTestUtils.getField(service, "rooms");
        return (ConcurrentHashMap<String, GobangRoom>) value;
    }
}
