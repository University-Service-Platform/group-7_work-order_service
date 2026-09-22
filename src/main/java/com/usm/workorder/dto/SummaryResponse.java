package com.usm.workorder.dto;

import java.util.Map;

/** Shares FR-12 (guide §4.2) - GET /api/work-orders/summary. */
public record SummaryResponse(String groupBy, Map<String, Long> counts) {
}
