package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏中心游戏卡片
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameDefinitionVO {

    // 游戏编码
    private String gameCode;

    // 游戏名称
    private String gameName;

    // 封面图地址
    private String coverUrl;

    // 是否启用
    private Boolean enabled;

    // 在线人数
    private Integer onlineCount;
}
