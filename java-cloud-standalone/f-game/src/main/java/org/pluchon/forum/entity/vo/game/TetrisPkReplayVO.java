package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块 PK 回放
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisPkReplayVO {

    // 对局记录
    private TetrisPkRecordVO record;

    // 回放 JSON
    private String replayPayload;
}
