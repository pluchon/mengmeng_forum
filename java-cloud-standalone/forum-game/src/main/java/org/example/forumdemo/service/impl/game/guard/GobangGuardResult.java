package org.example.forumdemo.service.impl.game.guard;

public class GobangGuardResult {

    private static final GobangGuardResult PASSED = new GobangGuardResult(true, null);

    private final boolean passed;

    private final String message;

    private GobangGuardResult(boolean passed, String message) {
        this.passed = passed;
        this.message = message;
    }

    public static GobangGuardResult pass() {
        return PASSED;
    }

    public static GobangGuardResult fail(String message) {
        return new GobangGuardResult(false, message);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}
