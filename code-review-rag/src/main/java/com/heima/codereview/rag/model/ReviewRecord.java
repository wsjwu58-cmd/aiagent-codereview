package com.heima.codereview.rag.model;

public record ReviewRecord(String id,
                           String reviewId,
                           String sessionId,
                           String content,
                           String projectId,
                           long timestamp) {
}
