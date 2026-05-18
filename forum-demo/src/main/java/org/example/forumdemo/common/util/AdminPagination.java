package org.example.forumdemo.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 兼容 Gi 后台分页参数：{@code page}+{@code size} 或 {@code pageNum}+{@code pageSize}
 */
public final class AdminPagination {

    private AdminPagination() {
    }

    public static <T> Page<T> of(Integer page, Integer size, Integer pageNum, Integer pageSize) {
        int pn = pageNum != null ? pageNum : (page != null ? page : 1);
        int ps = pageSize != null ? pageSize : (size != null ? size : 10);
        if (pn < 1) {
            pn = 1;
        }
        if (ps < 1) {
            ps = 10;
        }
        return new Page<>(pn, ps);
    }
}
