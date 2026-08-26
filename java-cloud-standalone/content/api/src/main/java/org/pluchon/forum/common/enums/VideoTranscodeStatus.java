package org.pluchon.forum.common.enums;

// 帖子视频 HLS 转码状态
public enum VideoTranscodeStatus {
    NONE((byte) 0),
    PROCESSING((byte) 1),
    READY((byte) 2),
    FAILED((byte) 3);

    private final byte code;

    VideoTranscodeStatus(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static VideoTranscodeStatus fromCode(Byte code) {
        if (code == null) {
            return NONE;
        }
        for (VideoTranscodeStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return NONE;
    }
}
