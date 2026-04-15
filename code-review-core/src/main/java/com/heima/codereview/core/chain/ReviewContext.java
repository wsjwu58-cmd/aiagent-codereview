package com.heima.codereview.core.chain;

import com.heima.codereview.common.model.chat.ChatMessage;
import com.heima.codereview.common.model.review.ReviewType;

import java.util.List;

public record ReviewContext(String reviewId,
                            String sessionId,
                            String projectId,
                            String language,
                            ReviewType reviewType,
                            String repoUrl,
                            String branch,
                            String codeContent,
                            List<ChatMessage> recentMessages,
                            List<String> historicalReviews) {
}
