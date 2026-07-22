package org.example.forumdemo.entity.vo.search;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 搜索用户结果字段契约测试
class SearchUserItemContractTest {

    @Test
    void searchUserItemShouldExposeFollowStatsAndState() throws Exception {
        Class<?> type = Class.forName("org.example.forumdemo.entity.vo.search.SearchUserItemVO");
        Set<String> fields = Stream.of(type.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("id", "nickname", "avatarUrl", "vipTier", "vipExpireAt",
                        "followingCount", "followerCount", "isFollowing"),
                fields
        );
    }
}
