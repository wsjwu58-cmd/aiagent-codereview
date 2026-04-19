package com.heima.codereview.tools.search;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class CodeSearchTool {

    private static final int DEFAULT_LIMIT = 10;

    public String search(String repoUrl, String query, String language, int limit) {
        Path basePath = resolveBasePath(repoUrl);
        if (basePath == null) {
            return "code_search unavailable: repoUrl must point to a local repository path.";
        }
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        int realLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 50);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(basePath, 8)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(item -> !item.toString().contains("\\.git\\"))
                    .filter(item -> matchesLanguage(item, language))
                    .limit(2000)
                    .toList();
            for (Path file : files) {
                searchInFile(basePath, file, normalizedQuery, matches, realLimit);
                if (matches.size() >= realLimit) {
                    break;
                }
            }
        } catch (IOException e) {
            return "code_search failed: " + e.getMessage();
        }
        if (matches.isEmpty()) {
            return "No code matches found for query: " + query;
        }
        return String.join("\n", matches);
    }

    private void searchInFile(Path basePath,
                              Path file,
                              String normalizedQuery,
                              List<String> matches,
                              int limit) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String normalizedLine = line.toLowerCase(Locale.ROOT);
                if (normalizedQuery.isBlank()) {
                    matches.add(relativize(basePath, file) + ":" + lineNumber + ": " + preview(line));
                } else if (normalizedLine.contains(normalizedQuery)
                        || file.getFileName().toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    matches.add(relativize(basePath, file) + ":" + lineNumber + ": " + preview(line));
                }
                if (matches.size() >= limit) {
                    return;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private boolean matchesLanguage(Path file, String language) {
        String normalizedLanguage = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if (normalizedLanguage.isBlank()) {
            return true;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return switch (normalizedLanguage) {
            case "java" -> name.endsWith(".java");
            case "python" -> name.endsWith(".py");
            case "javascript" -> name.endsWith(".js") || name.endsWith(".jsx");
            case "typescript" -> name.endsWith(".ts") || name.endsWith(".tsx");
            case "go" -> name.endsWith(".go");
            case "kotlin" -> name.endsWith(".kt");
            case "rust" -> name.endsWith(".rs");
            case "sql" -> name.endsWith(".sql");
            case "xml" -> name.endsWith(".xml");
            case "yaml" -> name.endsWith(".yml") || name.endsWith(".yaml");
            default -> true;
        };
    }

    private Path resolveBasePath(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }
        try {
            Path path = Path.of(repoUrl).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
            if (Files.isRegularFile(path)) {
                return path.getParent();
            }
        } catch (InvalidPathException ignored) {
        }
        return null;
    }

    private String relativize(Path basePath, Path file) {
        try {
            return basePath.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.toString().replace('\\', '/');
        }
    }

    private String preview(String line) {
        String normalized = line == null ? "" : line.trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
    }
}
