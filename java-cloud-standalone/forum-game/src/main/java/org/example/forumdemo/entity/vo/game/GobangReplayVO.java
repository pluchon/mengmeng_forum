package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 五子棋录像回放数据
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangReplayVO {

    // 对局记录
    private GameMatchRecordVO record;

    // 落子列表
    private List<GobangMoveVO> moves;
}
