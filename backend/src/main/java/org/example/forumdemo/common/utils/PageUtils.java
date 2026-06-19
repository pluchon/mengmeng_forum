package org.example.forumdemo.common.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class PageUtils {

    /**
     * 获取校验后的当前页码
     */
    public static int getValidPageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    /**
     * 获取校验后的每页数量
     */
    public static int getValidPageSize(Integer pageSize) {
        return (pageSize == null || pageSize < 1) ? 10 : pageSize;
    }

    /**
     * 获取组装好的 MyBatis Plus 分页对象
     */
    public static <T> Page<T> getPage(Integer pageNum, Integer pageSize) {
        return new Page<>(getValidPageNum(pageNum), getValidPageSize(pageSize));
    }
}
