package com.stagepass.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds safe {@link Pageable}s from query parameters: page ≥ 0, size 1..100 (default 20). */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : Math.max(1, Math.min(MAX_SIZE, size));
        return PageRequest.of(p, s, sort);
    }
}
