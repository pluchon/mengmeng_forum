package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块用户资料
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisProfileVO {

    // 用户 ID
    private Long userId;

    // 用户名
    private String username;

    // 昵称
    private String nickname;

    // 头像
    private String avatarUrl;

    // 历史最高分
    private Integer bestScore;

    // 总局数
    private Integer totalCount;

    // 论坛积分余额
    private Integer forumPoints;
}
