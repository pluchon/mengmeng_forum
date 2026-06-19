package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 游戏中心首页概览
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameCenterOverviewVO {

    // 游戏卡片列表
    private List<GameDefinitionVO> games;

    // 当前用户五子棋资料
    private GameUserProfileVO gobangProfile;

    // 游戏中心在线人数
    private Integer lobbyOnlineCount;
}
