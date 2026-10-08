package com.HyperLocalSystem.app.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {
}
