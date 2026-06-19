package org.example.forumdemo.common.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

// 针对后台管理系统做的工具类
// 统一解决前后端交互时分页参数命名不一致的痛点，并对参数做安全兜底，最终输出 MyBatis-Plus 的 Page 分页对象
public final class AdminPagination {

    private AdminPagination() {}

    // 也就是说，我们前端传过来的参数命名组合无论是什么，都可以通用
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
