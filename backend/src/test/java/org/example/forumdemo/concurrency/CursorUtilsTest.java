package org.example.forumdemo.concurrency;

import org.example.forumdemo.common.utils.CursorUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

class CursorUtilsTest {

    @Test
    void encodeDecode_roundTrip() {
        Date time = new Date(1_700_000_000_000L);
        long id = 42L;
        String cursor = CursorUtils.encode(time, id);
        CursorUtils.CursorToken token = CursorUtils.decode(cursor);
        Assertions.assertEquals(time.getTime(), token.timeMillis());
        Assertions.assertEquals(id, token.id());
    }
}
