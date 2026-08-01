package org.example.forumdemo.entity.vo.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 搜索用户列表项
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchUserItemVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    private Byte vipTier;

    private Date vipExpireAt;

    private Long followingCount;

    private Long followerCount;

    private Boolean isFollowing;
}
