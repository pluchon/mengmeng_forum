package org.pluchon.forum.entity.bo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏匹配分桶，保存用户应进入的队列名
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMatchBucket {

    // 分桶编码
    private String bucketCode;
}
