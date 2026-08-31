package org.pluchon.forum.service.impl.game;

import org.pluchon.forum.entity.vo.common.PageResult;

import java.util.List;

/**
 * 活跃房间的内存分页。
 *
 * <p>房间本来就活在 ConcurrentHashMap 里，没有数据库可以下推，所以分页只能在内存做。
 * 但把切片提前到查用户信息之前，仍然能省掉「为了显示 10 条而把全部对局者查一遍」。
 */
public final class GameActiveRoomPaging {

    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 50;

    private GameActiveRoomPaging() {
    }

    public static int validPageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public static int validPageSize(Integer pageSize) {
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public static <T> List<T> slice(List<T> all, Integer pageNum, Integer pageSize) {
        int size = validPageSize(pageSize);
        int page = clampPage(validPageNum(pageNum), all.size(), size);
        int from = (page - 1) * size;
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList(from, Math.min(from + size, all.size()));
    }

    public static <T> PageResult<T> toPage(List<T> records, int total, Integer pageNum, Integer pageSize) {
        int size = validPageSize(pageSize);
        long pages = total == 0 ? 1 : (total + size - 1L) / size;
        int page = clampPage(validPageNum(pageNum), total, size);
        return new PageResult<>(records, (long) total, page, size, pages, page < pages);
    }

    public static <T> PageResult<T> emptyPage(Integer pageNum, Integer pageSize) {
        return new PageResult<>(List.of(), 0L, validPageNum(pageNum), validPageSize(pageSize), 1L, false);
    }

    // 房间会不断开始与结束，停在越界页上会拿到空列表，统一收回最后一页
    private static int clampPage(int page, int total, int size) {
        int pages = total == 0 ? 1 : (total + size - 1) / size;
        return Math.min(page, pages);
    }
}
