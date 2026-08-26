package org.pluchon.forum.api;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// 用户字面搜索分页 auth 域权威
@Getter
public class UserSearchPageInternalVO {

    private List<UserInternalVO> records = new ArrayList<>();

    @Setter
    private long total;

    @Setter
    private int pageNum;

    @Setter
    private int pageSize;

    @Setter
    private long pages;

    @Setter
    private boolean hasNext;

    public void setRecords(List<UserInternalVO> records) {
        this.records = records != null ? records : new ArrayList<>();
    }
}
