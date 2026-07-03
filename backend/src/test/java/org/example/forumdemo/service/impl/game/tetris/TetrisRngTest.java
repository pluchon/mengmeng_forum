package org.example.forumdemo.service.impl.game.tetris;

import org.example.forumdemo.service.impl.game.TetrisRoom;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TetrisRngTest {

    @Test
    void pickBlockTypeShouldUseBalancedSevenBag() {
        TetrisRng rng = TetrisRng.create(123456L);

        for (int bagIndex = 0; bagIndex < 20; bagIndex++) {
            Set<String> types = new HashSet<>();
            for (int i = 0; i < TetrisEngineConstants.BLOCK_TYPES.size(); i++) {
                types.add(rng.pickBlockType());
            }

            assertEquals(TetrisEngineConstants.BLOCK_TYPES.size(), types.size());
            assertEquals(new HashSet<>(TetrisEngineConstants.BLOCK_TYPES), types);
        }
    }

    @Test
    void pkRoomPlayersShouldShareSameSeed() {
        TetrisRoom room = new TetrisRoom(1L, 2L, 1L, 2L);

        assertEquals(room.getPlayer1State().getSeed(), room.getPlayer2State().getSeed());
    }
}
