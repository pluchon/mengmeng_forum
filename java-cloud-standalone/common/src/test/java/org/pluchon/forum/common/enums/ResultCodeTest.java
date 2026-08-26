package org.pluchon.forum.common.enums;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

// 统一业务码完整性测试
class ResultCodeTest {

    @Test
    void allResultCodesMustBeUnique() {
        Set<Integer> codes = new HashSet<>();
        for (ResultCode resultCode : ResultCode.values()) {
            assertTrue(codes.add(resultCode.getCode()), "重复业务码: " + resultCode.getCode());
        }
    }
}
