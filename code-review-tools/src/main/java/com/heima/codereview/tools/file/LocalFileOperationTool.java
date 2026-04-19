package com.heima.codereview.tools.file;

import com.heima.codereview.tools.exception.ToolExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class LocalFileOperationTool {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final List<Path> allowedRoots;

    public LocalFileOperationTool(@Value("${code-review.local-code.allowed-paths:}") List<String> configuredRoots) {
        List<Path> roots = new ArrayList<>();
        if (configuredRoots != null) {
            for (String configuredRoot : configuredRoots) {
                Path path = normalizePath(configuredRoot);
                if (path != null) {
                    roots.add(path);
                }
            }
        }
        if (roots.isEmpty()) {
            roots.add(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize());
        }
        this.allowedRoots = List.copyOf(roots);
    }

    public List<String> listFiles(String folderPath, List<String> filters) {
        Path folder = resolveAllowedPath(folderPath, true);
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesFilters(path, filters))
                    .sorted(Comparator.naturalOrder())
                    .map(path -> folder.relativize(path).toString().replace('\\', '/'))
                    .toList();
        } catch (IOException e) {
            throw new ToolExecutionException("list_files failed: " + e.getMessage(), e);
        }
    }

    public String readFile(String filePath) {
        Path path = resolveAllowedPath(filePath, false);
        try {
            if (Files.size(path) > MAX_FILE_SIZE) {
                throw new ToolExecutionException("read_file rejected: file exceeds 10MB limit.");
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolExecutionException("read_file failed: " + e.getMessage(), e);
        }
    }

    public void writeFile(String filePath, String content) {
        Path path = resolveAllowedPath(filePath, false);
        try {
            Files.writeString(path, defaultString(content), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolExecutionException("write_file failed: " + e.getMessage(), e);
        }
    }

    public void createFile(String filePath, String content) {
        Path path = resolveAllowedPath(filePath, false);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, defaultString(content), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolExecutionException("create_file failed: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String filePath) {
        Path path = resolveAllowedPath(filePath, false);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new ToolExecutionException("delete_file failed: " + e.getMessage(), e);
        }
    }

    public List<String> searchFiles(String folderPath, String pattern, List<String> filters) {
        Path folder = resolveAllowedPath(folderPath, true);
        String normalizedPattern = defaultString(pattern).toLowerCase(Locale.ROOT);
        if (normalizedPattern.isBlank()) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesFilters(path, filters))
                    .filter(path -> fileContains(path, normalizedPattern))
                    .sorted(Comparator.naturalOrder())
                    .map(path -> folder.relativize(path).toString().replace('\\', '/'))
                    .toList();
        } catch (IOException e) {
            throw new ToolExecutionException("search_files failed: " + e.getMessage(), e);
        }
    }

    public boolean isAllowed(String path) {
        Path candidate = normalizePath(path);
        if (candidate == null) {
            return false;
        }
        return !isWindowsCDrive(candidate);
    }

    public List<String> allowedRoots() {
        return List.of("all local drives except C:");
    }

    private Path resolveAllowedPath(String rawPath, boolean mustBeDirectory) {
        Path path = normalizePath(rawPath);
        if (path == null) {
            throw new ToolExecutionException("invalid local path: " + rawPath);
        }
        if (!isAllowed(path.toString())) {
            throw new ToolExecutionException("path is not allowed for local-code analysis: "
                    + rawPath
                    + " ; allowed scope="
                    + String.join(", ", allowedRoots()));
        }
        if (!Files.exists(path)) {
            throw new ToolExecutionException("path does not exist: " + rawPath);
        }
        if (mustBeDirectory && !Files.isDirectory(path)) {
            throw new ToolExecutionException("expected a directory path: " + rawPath);
        }
        if (!mustBeDirectory && Files.isDirectory(path)) {
            throw new ToolExecutionException("expected a file path: " + rawPath);
        }
        return path;
    }

    private Path normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        try {
            return Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private boolean isWindowsCDrive(Path path) {
        Path root = path.getRoot();
        if (root == null) {
            return false;
        }
        String normalizedRoot = root.toString().replace('\\', '/');
        return "C:/".equalsIgnoreCase(normalizedRoot) || "C:".equalsIgnoreCase(normalizedRoot);
    }

    private boolean matchesFilters(Path path, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        for (String filter : filters) {
            if (filter == null || filter.isBlank()) {
                continue;
            }
            String normalized = filter.trim();
            if (FileSystems.getDefault().getPathMatcher("glob:" + normalized).matches(Path.of(fileName))
                    || FileSystems.getDefault().getPathMatcher("glob:" + normalized).matches(path.getFileName())) {
                return true;
            }
        }
        return false;
    }

    private boolean fileContains(Path path, String normalizedPattern) {
        try {
            if (Files.size(path) > MAX_FILE_SIZE) {
                return false;
            }
            return Files.readString(path, StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT)
                    .contains(normalizedPattern);
        } catch (IOException e) {
            return false;
        }
    }

    private String defaultString(String value) {
        return Objects.toString(value, "");
    }
}
