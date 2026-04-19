package com.heima.codereview.tools.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final String SERPER_DEFAULT_URL = "https://google.serper.dev/search";
    private static final String TAVILY_DEFAULT_URL = "https://api.tavily.com/search";
    private static final String SEARXNG_DEFAULT_URL = "http://localhost:8081/search";

    @Value("${code-review.web-search.enabled:${WEB_SEARCH_ENABLED:false}}")
    private boolean enabled;

    @Value("${code-review.web-search.provider:${WEB_SEARCH_PROVIDER:serper}}")
    private String provider;

    @Value("${code-review.web-search.api-key:${WEB_SEARCH_API_KEY:}}")
    private String apiKey;

    @Value("${code-review.web-search.base-url:${WEB_SEARCH_BASE_URL:}}")
    private String baseUrl;

    @Value("${code-review.web-search.region:${WEB_SEARCH_REGION:us}}")
    private String region;

    @Value("${code-review.web-search.language:${WEB_SEARCH_LANGUAGE:zh-cn}}")
    private String language;

    @Value("${code-review.web-search.search-depth:${WEB_SEARCH_SEARCH_DEPTH:basic}}")
    private String searchDepth;

    @Value("${code-review.web-search.include-answer:${WEB_SEARCH_INCLUDE_ANSWER:true}}")
    private boolean includeAnswer;

    @Value("${code-review.web-search.snippet-max-length:${WEB_SEARCH_SNIPPET_MAX_LENGTH:280}}")
    private int snippetMaxLength;

    public String search(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return "web_search rejected: query must not be blank.";
        }
        if (!enabled) {
            return "web_search is disabled. Set code-review.web-search.enabled=true and configure a provider.";
        }

        int realLimit = normalizeLimit(limit);
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        try {
            return switch (normalizedProvider) {
                case "serper" -> searchBySerper(normalizedQuery, realLimit);
                case "tavily" -> searchByTavily(normalizedQuery, realLimit);
                case "searxng" -> searchBySearxng(normalizedQuery, realLimit);
                default -> "web_search provider is unsupported: " + provider
                        + ". Supported providers: serper, tavily, searxng.";
            };
        } catch (Exception e) {
            log.error("web_search failed. provider={}, query={}, reason={}", normalizedProvider, normalizedQuery, e.getMessage(), e);
            return "web_search failed: " + e.getMessage();
        }
    }

    private String searchBySerper(String query, int limit) throws Exception {
        if (!hasText(apiKey)) {
            return "web_search provider serper requires WEB_SEARCH_API_KEY.";
        }
        String targetUrl = resolveEndpoint(baseUrl, SERPER_DEFAULT_URL, "/search");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-API-KEY", apiKey);
        Map<String, Object> body = Map.of(
                "q", query,
                "num", limit,
                "gl", defaultString(region, "us"),
                "hl", defaultString(language, "zh-cn")
        );
        JsonNode root = post(targetUrl, headers, body);
        List<SearchResult> results = new ArrayList<>();
        JsonNode organic = root.path("organic");
        if (organic.isArray()) {
            for (JsonNode item : organic) {
                results.add(new SearchResult(
                        item.path("title").asText(),
                        item.path("link").asText(),
                        item.path("snippet").asText()
                ));
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        return formatResults("serper", query, results, "");
    }

    private String searchByTavily(String query, int limit) throws Exception {
        if (!hasText(apiKey)) {
            return "web_search provider tavily requires WEB_SEARCH_API_KEY.";
        }
        String targetUrl = resolveEndpoint(baseUrl, TAVILY_DEFAULT_URL, "/search");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        Map<String, Object> body = Map.of(
                "api_key", apiKey,
                "query", query,
                "max_results", limit,
                "search_depth", defaultString(searchDepth, "basic"),
                "include_answer", includeAnswer,
                "include_raw_content", false
        );
        JsonNode root = post(targetUrl, headers, body);
        String answer = root.path("answer").asText();
        List<SearchResult> results = new ArrayList<>();
        JsonNode resultNode = root.path("results");
        if (resultNode.isArray()) {
            for (JsonNode item : resultNode) {
                results.add(new SearchResult(
                        item.path("title").asText(),
                        item.path("url").asText(),
                        item.path("content").asText()
                ));
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        return formatResults("tavily", query, results, answer);
    }

    private String searchBySearxng(String query, int limit) throws Exception {
        String targetUrl = resolveEndpoint(baseUrl, SEARXNG_DEFAULT_URL, "/search");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(targetUrl)
                .queryParam("q", query)
                .queryParam("format", "json")
                .queryParam("language", defaultString(language, "zh-CN"));
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        JsonNode root = get(builder.encode(StandardCharsets.UTF_8).toUriString(), headers);
        List<SearchResult> results = new ArrayList<>();
        JsonNode resultNode = root.path("results");
        if (resultNode.isArray()) {
            for (JsonNode item : resultNode) {
                results.add(new SearchResult(
                        item.path("title").asText(),
                        item.path("url").asText(),
                        item.path("content").asText()
                ));
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        return formatResults("searxng", query, results, "");
    }

    private JsonNode post(String url, HttpHeaders headers, Map<String, Object> body) throws Exception {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return parseJson(response.getBody());
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("provider returned HTTP " + e.getRawStatusCode()
                    + errorBodySuffix(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("provider request failed: " + e.getMessage(), e);
        }
    }

    private JsonNode get(String url, HttpHeaders headers) throws Exception {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseJson(response.getBody());
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("provider returned HTTP " + e.getRawStatusCode()
                    + errorBodySuffix(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("provider request failed: " + e.getMessage(), e);
        }
    }

    private JsonNode parseJson(String body) throws Exception {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("search provider returned empty body");
        }
        return OBJECT_MAPPER.readTree(body);
    }

    private String formatResults(String providerName, String query, List<SearchResult> results, String answer) {
        if (results.isEmpty()) {
            return "web_search returned no results. provider=" + providerName + ", query=\"" + query + "\"";
        }
        List<String> lines = new ArrayList<>();
        lines.add("provider=" + providerName);
        lines.add("query=" + query);
        if (hasText(answer)) {
            lines.add("answer=" + trimSnippet(answer));
        }
        lines.add("results=");
        for (int index = 0; index < results.size(); index++) {
            SearchResult item = results.get(index);
            lines.add((index + 1) + ". " + defaultString(item.title(), "(untitled)"));
            lines.add("   url: " + defaultString(item.url(), ""));
            if (hasText(item.snippet())) {
                lines.add("   snippet: " + trimSnippet(item.snippet()));
            }
        }
        return String.join("\n", lines);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String resolveEndpoint(String configuredUrl, String defaultUrl, String rootPathSuffix) {
        if (!hasText(configuredUrl)) {
            return defaultUrl;
        }
        String normalized = configuredUrl.trim();
        try {
            URI uri = URI.create(normalized);
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return normalized.endsWith("/")
                        ? normalized.substring(0, normalized.length() - 1) + rootPathSuffix
                        : normalized + rootPathSuffix;
            }
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring malformed web_search base-url: {}", normalized);
            return defaultUrl;
        }
        return normalized;
    }

    private String errorBodySuffix(String responseBody) {
        String preview = trimSnippet(responseBody);
        if (!hasText(preview)) {
            return "";
        }
        return ", body=" + preview;
    }

    private String trimSnippet(String text) {
        String normalized = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= snippetMaxLength) {
            return normalized;
        }
        return normalized.substring(0, snippetMaxLength) + "...";
    }

    private String defaultString(String text, String fallback) {
        return hasText(text) ? text.trim() : fallback;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private record SearchResult(String title, String url, String snippet) {
    }
}
