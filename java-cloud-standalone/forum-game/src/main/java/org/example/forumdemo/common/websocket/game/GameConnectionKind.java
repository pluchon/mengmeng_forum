package org.example.forumdemo.common.websocket.game;

// 游戏 WebSocket 连接类型，用于连接清理和防止不同通道互相覆盖
public enum GameConnectionKind {
    LOBBY,
    GAME,
    ROOM
}
