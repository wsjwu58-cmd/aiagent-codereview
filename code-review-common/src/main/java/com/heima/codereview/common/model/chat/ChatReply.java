package com.heima.codereview.common.model.chat;

import java.util.List;

public record ChatReply(String sessionId, String answer, List<String> references) {
}
