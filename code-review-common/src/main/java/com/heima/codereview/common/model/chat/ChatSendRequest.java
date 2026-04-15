package com.heima.codereview.common.model.chat;

public record ChatSendRequest(String sessionId, String message, String projectId) {
}
