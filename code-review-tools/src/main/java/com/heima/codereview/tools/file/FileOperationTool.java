package com.heima.codereview.tools.file;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class FileOperationTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_READ_LENGTH = 6000;

    public String operate(String repoUrl, String path, String action, String keyword, int limit) {
        Path basePath = resolveBasePath(repoUrl);
        if (basePath == null) {
            return "file_operation unavailable: repoUrl must point to a local repository path.";
        }
        Path target = resolveTarget(basePath, path);
        if (target == null || !target.startsWith(basePath)) {
            return "file_operation rejected: target path is invalid or outside the repository root.";
        }

        String normalizedAction = action == null || action.isBlank() ? "list" : action.toLowerCase(Locale.ROOT);
        int realLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 100);
        try {
            return switch (normalizedAction) {
                case "read" -> readFile(basePath, target);
                case "list" -> listFiles(basePath, target, keyword, realLimit);
                default -> "file_operation only supports action=list or action=read.";
            };
        } catch (IOException e) {
            return "file_operation failed: " + e.getMessage();
        }
    }

    private String readFile(Path basePath, Path target) throws IOException {
        if (!Files.exists(target)) {
            return "Target file does not exist: " + target;
        }
        if (Files.isDirectory(target)) {
            return "Target path is a directory. Use action=list instead: " + relativize(basePath, target);
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        if (content.length() > MAX_READ_LENGTH) {
            content = content.substring(0, MAX_READ_LENGTH) + "\n...[truncated]";
        }
        return relativize(basePath, target) + "\n" + content;
    }

    private String listFiles(Path basePath, Path target, String keyword, int limit) throws IOException {
        Path start = Files.isDirectory(target) ? target : target.getParent();
        if (start == null || !Files.exists(start)) {
            return "Target path does not exist: " + target;
        }
        String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        try (Stream<Path> stream = Files.walk(start, 4)) {
            List<String> results = stream
                    .filter(Files::isRegularFile)
                    .filter(item -> {
                        String normalized = item.toString().replace('\\', '/');
                        return !normalized.contains("/.git/");
                    })
                    .map(item -> relativize(basePath, item))
                    .filter(item -> normalizedKeyword.isBlank() || item.toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                    .sorted(Comparator.naturalOrder())
                    .limit(limit)
                    .toList();
            if (results.isEmpty()) {
                return "No files matched the request under " + relativize(basePath, start);
            }
            return String.join("\n", results);
        }
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

    private Path resolveTarget(Path basePath, String path) {
        if (path == null || path.isBlank()) {
            return basePath;
        }
        try {
            Path candidate = Path.of(path);
            if (!candidate.isAbsolute()) {
                candidate = basePath.resolve(path);
            }
            return candidate.toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String relativize(Path basePath, Path path) {
        try {
            return basePath.relativize(path).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString().replace('\\', '/');
        }
    }
}
