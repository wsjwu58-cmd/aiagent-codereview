package com.heima.codereview.common.model.history;

public record HistoryRecord(String id, String sessionId, String summary, long timestamp) {
}
