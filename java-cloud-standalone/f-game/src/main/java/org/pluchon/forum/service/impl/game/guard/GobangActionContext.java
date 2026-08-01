package org.pluchon.forum.service.impl.game.guard;

import org.pluchon.forum.entity.dto.game.GobangChatRequest;
import org.pluchon.forum.service.impl.game.GobangRoom;

public class GobangActionContext {

    private final GobangActionType actionType;

    private final String roomId;

    private final Long userId;

    private final String requestId;

    private final GobangRoom room;

    private final Integer row;

    private final Integer col;

    private final GobangChatRequest chatRequest;

    private GobangActionContext(
            GobangActionType actionType,
            String roomId,
            Long userId,
            String requestId,
            GobangRoom room,
            Integer row,
            Integer col,
            GobangChatRequest chatRequest
    ) {
        this.actionType = actionType;
        this.roomId = roomId;
        this.userId = userId;
        this.requestId = requestId;
        this.room = room;
        this.row = row;
        this.col = col;
        this.chatRequest = chatRequest;
    }

    public static GobangActionContext move(String roomId, Long userId, String requestId,
                                           GobangRoom room, Integer row, Integer col) {
        return new GobangActionContext(GobangActionType.MOVE, roomId, userId, requestId, room, row, col, null);
    }

    public static GobangActionContext chat(String roomId, Long userId, String requestId,
                                           GobangRoom room, GobangChatRequest request) {
        return new GobangActionContext(GobangActionType.CHAT, roomId, userId, requestId, room, null, null, request);
    }

    public static GobangActionContext surrender(String roomId, Long userId, String requestId, GobangRoom room) {
        return new GobangActionContext(GobangActionType.SURRENDER, roomId, userId, requestId, room, null, null, null);
    }

    public GobangActionType getActionType() {
        return actionType;
    }

    public String getRoomId() {
        return roomId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRequestId() {
        return requestId;
    }

    public GobangRoom getRoom() {
        return room;
    }

    public Integer getRow() {
        return row;
    }

    public Integer getCol() {
        return col;
    }

    public GobangChatRequest getChatRequest() {
        return chatRequest;
    }

    public String chatMessageType() {
        return chatRequest == null || chatRequest.getMessageType() == null
                ? "TEXT"
                : chatRequest.getMessageType().trim().toUpperCase();
    }

    public String chatContent() {
        return chatRequest == null || chatRequest.getContent() == null
                ? ""
                : chatRequest.getContent().trim();
    }
}
