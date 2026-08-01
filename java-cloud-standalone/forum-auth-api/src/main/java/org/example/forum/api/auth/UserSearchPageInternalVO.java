package org.example.forum.api.auth;

import java.util.ArrayList;
import java.util.List;

// 用户字面搜索分页（auth 域权威）
public class UserSearchPageInternalVO {

    private List<UserInternalVO> records = new ArrayList<>();
    private long total;
    private int pageNum;
    private int pageSize;
    private long pages;
    private boolean hasNext;

    public List<UserInternalVO> getRecords() {
        return records;
    }

    public void setRecords(List<UserInternalVO> records) {
        this.records = records != null ? records : new ArrayList<>();
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
}
