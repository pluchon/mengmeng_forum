package org.pluchon.forum.service.interfaces.game;

public interface GameOnlineStateService {

    void enterLobby(Long userId);

    void leaveLobby(Long userId);

    void touchLobby(Long userId);

    int countLobbyOnline();

    void enterGame(String gameCode, Long userId);

    void leaveGame(String gameCode, Long userId);

    void touchGame(String gameCode, Long userId);

    int countGameOnline(String gameCode);
}
