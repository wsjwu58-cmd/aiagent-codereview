package com.heima.codereview.common.model.chat;

public record ChatMessage(String role, String content, long timestamp) {
}
