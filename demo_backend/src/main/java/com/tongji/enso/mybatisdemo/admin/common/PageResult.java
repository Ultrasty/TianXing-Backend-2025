package com.tongji.enso.mybatisdemo.admin.common;

import java.util.List;

public class PageResult<T> {
    private final int page;
    private final int pageSize;
    private final long total;
    private final List<T> items;

    public PageResult(int page, int pageSize, long total, List<T> items) {
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotal() {
        return total;
    }

    public List<T> getItems() {
        return items;
    }
}
