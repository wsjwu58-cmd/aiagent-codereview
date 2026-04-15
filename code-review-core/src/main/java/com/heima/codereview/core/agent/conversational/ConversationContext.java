package com.heima.codereview.core.agent.conversational;

import java.util.ArrayList;
import java.util.List;

public record ConversationContext(
        String sessionId,
        String projectId,
        String repoUrl,
        String branch,
        String language,
        String repositoryContext,
        String codeContent,
        List<String> recentMessages,
        List<String> crossSessionMemories,
        List<String> relatedReviews,
        List<String> relevantNorms
) {

    public List<String> allReferences() {
        List<String> references = new ArrayList<>();
        references.add(joinSingle("Repository", repositorySummary()));
        references.add(joinSection("Recent Messages", recentMessages));
        references.add(joinSection("Cross Session Memory", crossSessionMemories));
        references.add(joinSection("Related Reviews", relatedReviews));
        references.add(joinSection("Norms", relevantNorms));
        references.add(joinSingle("Repository Diff", repositoryContext));
        return List.copyOf(references);
    }

    public String fullCodeContext() {
        if (codeContent != null && !codeContent.isBlank()) {
            return codeContent;
        }
        return repositoryContext;
    }

    public boolean hasFullCode() {
        return codeContent != null && !codeContent.isBlank();
    }

    public String repositorySummary() {
        StringBuilder builder = new StringBuilder();
        appendField(builder, "repoUrl", repoUrl);
        appendField(builder, "branch", branch);
        appendField(builder, "language", language);
        if (builder.length() == 0) {
            return "N/A";
        }
        return builder.toString();
    }

    public boolean hasRepositoryContext() {
        return repositoryContext != null && !repositoryContext.isBlank();
    }

    private String joinSection(String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return title + ": N/A";
        }
        return title + ":\n- " + String.join("\n- ", items);
    }

    private String joinSingle(String title, String value) {
        if (value == null || value.isBlank()) {
            return title + ": N/A";
        }
        return title + ":\n" + value;
    }

    private void appendField(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(value);
    }
}
