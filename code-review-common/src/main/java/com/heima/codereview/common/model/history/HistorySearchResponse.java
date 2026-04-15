package com.heima.codereview.common.model.history;

import java.util.List;

public record HistorySearchResponse(String keyword, List<HistoryRecord> records) {
}
