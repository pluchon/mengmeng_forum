package org.pluchon.forum.service.impl.game.matchguard;

public class GobangMatchGuardResult {

    public enum Type {
        PASS,
        FAIL,
        OK
    }

    private static final GobangMatchGuardResult PASSED = new GobangMatchGuardResult(Type.PASS, null);

    private static final GobangMatchGuardResult OK = new GobangMatchGuardResult(Type.OK, null);

    private final Type type;

    private final String message;

    private GobangMatchGuardResult(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public static GobangMatchGuardResult pass() {
        return PASSED;
    }

    public static GobangMatchGuardResult fail(String message) {
        return new GobangMatchGuardResult(Type.FAIL, message);
    }

    public static GobangMatchGuardResult ok() {
        return OK;
    }

    public boolean isPass() {
        return type == Type.PASS;
    }

    public boolean isFail() {
        return type == Type.FAIL;
    }

    public boolean isOk() {
        return type == Type.OK;
    }

    public String getMessage() {
        return message;
    }
}
