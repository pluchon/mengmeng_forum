package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块回放数据
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisReplayVO {

    // 记录摘要
    private TetrisRecordVO record;

    // 随机种子
    private Long seed;

    // 回放 JSON
    private String replayPayload;
}
