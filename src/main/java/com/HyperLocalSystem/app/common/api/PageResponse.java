package com.HyperLocalSystem.app.common.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** Clamps client-supplied paging so a request can never ask for an unbounded page. */
    public static Pageable request(int page, int size) {
        return PageRequest.of(Math.max(page, 0), size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE));
    }

    /** A short, naturally bounded list (a few offers, at most 10 addresses) in the same shape, as one page. */
    public static <T> PageResponse<T> all(List<T> items) {
        return new PageResponse<>(items, 0, items.size(), items.size());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
