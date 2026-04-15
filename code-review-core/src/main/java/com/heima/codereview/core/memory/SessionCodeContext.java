package com.heima.codereview.core.memory;

public record SessionCodeContext(
        String projectId,
        String repoUrl,
        String branch,
        String language,
        String content,
        long updatedAt
) {

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
