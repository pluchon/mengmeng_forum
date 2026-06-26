package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 井字棋连线坐标，用于结束高亮和回放展示
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JinziBoardPointVO {

    // 行号
    private Integer row;

    // 列号
    private Integer col;
}
