package com.heima.codereview.rag.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);
    private static final String PROMPT_PATH = "prompt/query-rewriter-prompt.md";

    private final ChatClient chatClient;

    public QueryRewriter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public List<String> rewrite(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            var resource = new ClassPathResource(PROMPT_PATH);
            var systemPrompt = new SystemPromptTemplate(resource).render();

            var response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(query)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return List.of(query);
            }

            return Arrays.stream(response.split("[,\n]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(5)
                    .toList();
        } catch (Exception e) {
            log.warn("Query rewriting failed, using original query: {}", query, e);
            return List.of(query);
        }
    }
}
