package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

/** AI 调用预记录 begin 结果 */
@Data
public class AiCallBeginResult {

    private Long recordId;

    private boolean duplicateSuccess;

    private boolean terminalFailure;

    private Integer previousPointsCharged;

    public static AiCallBeginResult pending(Long recordId) {
        AiCallBeginResult r = new AiCallBeginResult();
        r.setRecordId(recordId);
        return r;
    }

    public static AiCallBeginResult duplicateSuccess(Long recordId, int pointsCharged) {
        AiCallBeginResult r = new AiCallBeginResult();
        r.setRecordId(recordId);
        r.setDuplicateSuccess(true);
        r.setPreviousPointsCharged(pointsCharged);
        return r;
    }

    public static AiCallBeginResult terminalFailure(Long recordId) {
        AiCallBeginResult r = new AiCallBeginResult();
        r.setRecordId(recordId);
        r.setTerminalFailure(true);
        return r;
    }
}
