package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 五子棋房间参与者展示信息，用于棋手、AI 和观战席
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangRoomParticipantVO {

    // 用户 ID，AI 固定为 -1
    private Long userId;

    // 用户名
    private String username;

    // 昵称
    private String nickname;

    // 头像 URL
    private String avatarUrl;

    // VIP 档位
    private Byte vipTier;

    // 当前是否有效 VIP
    private Boolean vip;

    // 房间角色：BLACK/WHITE/SPECTATOR/AI
    private String roomRole;

    // 加入房间时间戳
    private Long joinedAtMs;

    // 是否虚拟对手
    private Boolean ai;

    // AI 模型/策略名称
    private String aiModelName;

    // 该游戏总对局数
    private Integer totalCount;

    // 该游戏胜率百分比
    private Integer winRate;
}
