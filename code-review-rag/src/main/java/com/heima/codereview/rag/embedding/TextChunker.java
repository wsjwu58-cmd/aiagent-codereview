package com.heima.codereview.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextChunker {

    private static final Logger log = LoggerFactory.getLogger(TextChunker.class);

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;
    private static final int MIN_SEMANTIC_CHUNK_SIZE = 180;
    private static final int MAX_SENTENCE_LENGTH = 220;
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[^.!?;。！？；\\n]+(?:[.!?;。！？；]+|\\n+|$)");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "^(?:@[\\w.()=,\\\"'\\s]+\\s*)*(?:public|private|protected|abstract|final|static|sealed|non-sealed|\\s)*\\s*(class|interface|enum|record)\\s+\\w+.*");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "^(?:@[\\w.()=,\\\"'\\s]+\\s*)*(?:public|private|protected|static|final|synchronized|native|abstract|async|\\s)+[\\w<>\\[\\],.?\\s]+\\s+\\w+\\s*\\([^;]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{?\\s*$");
    private static final Pattern FUNCTION_DECLARATION = Pattern.compile(
            "^(def|async\\s+def|function|func|fun)\\s+\\w+\\s*\\([^)]*\\).*");
    private static final Pattern DIFF_HUNK = Pattern.compile("^@@\\s+.*@@.*$");

    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;
    private final boolean semanticChunkingEnabled;
    private final double semanticBreakpointThreshold;

    public TextChunker(ObjectProvider<EmbeddingService> embeddingServiceProvider,
                       @Value("${code-review.rag.chunking.semantic-enabled:true}") boolean semanticChunkingEnabled,
                       @Value("${code-review.rag.chunking.semantic-breakpoint-threshold:0.58}") double semanticBreakpointThreshold) {
        this.embeddingServiceProvider = embeddingServiceProvider;
        this.semanticChunkingEnabled = semanticChunkingEnabled;
        this.semanticBreakpointThreshold = semanticBreakpointThreshold;
    }

    public enum ChunkProfile {
        GENERIC,
        CODE,
        PDF,
        CHAT
    }

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize) {
        return chunk(text, chunkSize, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        return chunk(text, chunkSize, overlap, ChunkProfile.GENERIC);
    }

    public List<String> chunk(String text, ChunkProfile profile) {
        return switch (profile == null ? ChunkProfile.GENERIC : profile) {
            case CODE -> chunk(text, 700, 80, ChunkProfile.CODE);
            case PDF -> chunk(text, 800, 100, ChunkProfile.PDF);
            case CHAT -> chunk(text, 380, 60, ChunkProfile.CHAT);
            case GENERIC -> chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP, ChunkProfile.GENERIC);
        };
    }

    public List<String> chunk(String text, int chunkSize, int overlap, ChunkProfile profile) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int realChunkSize = Math.max(100, chunkSize);
        int realOverlap = Math.max(0, Math.min(overlap, realChunkSize / 4));
        ChunkProfile realProfile = profile == null ? ChunkProfile.GENERIC : profile;

        if (realProfile == ChunkProfile.CODE || looksLikeCode(text)) {
            return chunkCodeStructure(text, realChunkSize, realOverlap);
        }
        if (semanticChunkingEnabled && (realProfile == ChunkProfile.PDF || realProfile == ChunkProfile.CHAT)) {
            return chunkSemantically(text, realChunkSize, realOverlap);
        }
        if (realProfile == ChunkProfile.PDF || realProfile == ChunkProfile.CHAT) {
            return chunkByNaturalBreaks(text, realChunkSize, realOverlap);
        }
        return chunkFixedWindow(text, realChunkSize, realOverlap);
    }

    private List<String> chunkCodeStructure(String text, int chunkSize, int overlap) {
        String normalized = normalizeLineEndings(text).trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        if (normalized.lines().anyMatch(line -> DIFF_HUNK.matcher(line.trim()).matches())) {
            return packBlocks(splitDiffBlocks(normalized), chunkSize, overlap);
        }
        List<String> blocks = splitCodeBlocks(normalized);
        return packBlocks(blocks.isEmpty() ? List.of(normalized) : blocks, chunkSize, overlap);
    }

    private List<String> splitDiffBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\n", -1)) {
            if (DIFF_HUNK.matcher(line.trim()).matches() && !current.isEmpty()) {
                addChunk(blocks, current.toString());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        addChunk(blocks, current.toString());
        return blocks;
    }

    private List<String> splitCodeBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder header = new StringBuilder();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int previousBraceDepth = 0;
        boolean structuralBlockStarted = false;

        for (String line : text.split("\\n", -1)) {
            String trimmed = line.trim();
            boolean topLevel = previousBraceDepth <= 0;
            boolean declaration = isCodeDeclaration(trimmed);
            boolean importOrPackage = isPackageOrImport(trimmed);

            if (!structuralBlockStarted && importOrPackage) {
                header.append(line).append('\n');
                continue;
            }

            if (topLevel && declaration && structuralBlockStarted && !current.isEmpty()) {
                addChunk(blocks, current.toString());
                current = new StringBuilder();
            }

            structuralBlockStarted = structuralBlockStarted || declaration || !trimmed.isBlank();
            if (current.isEmpty() && !header.isEmpty()) {
                current.append(header);
                header = new StringBuilder();
            }
            current.append(line).append('\n');

            braceDepth += braceDelta(line);
            if (structuralBlockStarted && braceDepth <= 0 && !current.isEmpty() && declarationEnded(trimmed)) {
                addChunk(blocks, current.toString());
                current = new StringBuilder();
                structuralBlockStarted = false;
                braceDepth = 0;
            }
            previousBraceDepth = braceDepth;
        }

        if (!header.isEmpty()) {
            current.insert(0, header);
        }
        addChunk(blocks, current.toString());
        return blocks;
    }

    private List<String> packBlocks(List<String> blocks, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (block == null || block.isBlank()) {
                continue;
            }
            String normalizedBlock = block.trim();
            if (normalizedBlock.length() > chunkSize * 2) {
                flush(chunks, current);
                chunks.addAll(splitOversizedCodeBlock(normalizedBlock, chunkSize, overlap));
                continue;
            }
            if (!current.isEmpty() && current.length() + normalizedBlock.length() + 2 > chunkSize) {
                flush(chunks, current);
                if (overlap > 0 && !chunks.isEmpty()) {
                    current.append(overlapPrefix(chunks.get(chunks.size() - 1), overlap));
                }
            }
            current.append(normalizedBlock).append("\n\n");
        }
        flush(chunks, current);
        return chunks;
    }

    private List<String> splitOversizedCodeBlock(String block, int chunkSize, int overlap) {
        List<String> lines = List.of(block.split("\\n", -1));
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (String line : lines) {
            current.append(line).append('\n');
            depth += braceDelta(line);
            boolean safeBoundary = depth <= 1 && (line.trim().isBlank() || line.trim().endsWith("}") || line.trim().endsWith(";"));
            if (current.length() >= chunkSize && safeBoundary) {
                flush(chunks, current);
                if (overlap > 0 && !chunks.isEmpty()) {
                    current.append(overlapPrefix(chunks.get(chunks.size() - 1), overlap));
                }
            }
        }
        flush(chunks, current);
        if (chunks.isEmpty()) {
            return chunkFixedWindow(block, chunkSize, overlap);
        }
        return chunks;
    }

    private List<String> chunkSemantically(String text, int chunkSize, int overlap) {
        List<String> sentences = splitSentences(text);
        if (sentences.size() <= 1) {
            return chunkByNaturalBreaks(text, chunkSize, overlap);
        }
        List<Double> adjacentSimilarities = adjacentSimilarities(sentences);
        if (adjacentSimilarities.isEmpty()) {
            return chunkByNaturalBreaks(text, chunkSize, overlap);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        List<Integer> boundaryIndexes = semanticBoundaryIndexes(sentences, adjacentSimilarities, chunkSize);
        Set<Integer> boundaries = new LinkedHashSet<>(boundaryIndexes);

        for (int index = 0; index < sentences.size(); index++) {
            current.append(sentences.get(index)).append(' ');
            boolean semanticBoundary = boundaries.contains(index);
            boolean maxExceeded = current.length() >= chunkSize;
            if ((semanticBoundary && current.length() >= MIN_SEMANTIC_CHUNK_SIZE) || maxExceeded) {
                flush(chunks, current);
                if (overlap > 0 && !chunks.isEmpty()) {
                    current.append(overlapPrefix(chunks.get(chunks.size() - 1), overlap));
                }
            }
        }
        flush(chunks, current);
        return chunks.isEmpty() ? chunkByNaturalBreaks(text, chunkSize, overlap) : chunks;
    }

    private List<Integer> semanticBoundaryIndexes(List<String> sentences, List<Double> similarities, int chunkSize) {
        List<Integer> boundaries = new ArrayList<>();
        int chunkStart = 0;
        int currentLength = 0;
        for (int index = 0; index < sentences.size(); index++) {
            currentLength += sentences.get(index).length() + 1;
            boolean enoughContent = currentLength >= Math.max(MIN_SEMANTIC_CHUNK_SIZE, chunkSize / 2);
            boolean hitTarget = currentLength >= chunkSize;
            if (!enoughContent && !hitTarget) {
                continue;
            }
            int searchEnd = Math.min(similarities.size() - 1, index);
            int searchStart = Math.max(chunkStart, searchEnd - 4);
            int bestBoundary = bestSemanticBoundary(similarities, searchStart, searchEnd);
            double bestScore = bestBoundary >= 0 ? similarities.get(bestBoundary) : 1.0d;
            if (hitTarget || bestScore <= semanticBreakpointThreshold) {
                boundaries.add(bestBoundary >= 0 ? bestBoundary : index);
                chunkStart = index + 1;
                currentLength = 0;
            }
        }
        return boundaries;
    }

    private int bestSemanticBoundary(List<Double> similarities, int start, int end) {
        if (start > end || start < 0) {
            return -1;
        }
        return java.util.stream.IntStream.rangeClosed(start, end)
                .boxed()
                .min(Comparator.comparingDouble(similarities::get))
                .orElse(-1);
    }

    private List<Double> adjacentSimilarities(List<String> sentences) {
        EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
        if (embeddingService == null) {
            return List.of();
        }
        try {
            List<List<Float>> vectors = new ArrayList<>(sentences.size());
            for (String sentence : sentences) {
                vectors.add(embeddingService.embed(sentence));
            }
            List<Double> similarities = new ArrayList<>(Math.max(0, vectors.size() - 1));
            for (int index = 0; index + 1 < vectors.size(); index++) {
                similarities.add(cosine(vectors.get(index), vectors.get(index + 1)));
            }
            return similarities;
        } catch (Exception e) {
            log.warn("Semantic chunking failed, using natural boundaries. reason={}", e.getMessage());
            return List.of();
        }
    }

    private double cosine(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }
        int size = Math.min(left.size(), right.size());
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < size; index++) {
            double l = left.get(index) == null ? 0.0d : left.get(index);
            double r = right.get(index) == null ? 0.0d : right.get(index);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<String> splitSentences(String text) {
        String normalized = normalizeLineEndings(text).replaceAll("\\n{2,}", "\n").trim();
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_SPLIT.matcher(normalized);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.isBlank()) {
                continue;
            }
            if (sentence.length() <= MAX_SENTENCE_LENGTH) {
                sentences.add(sentence);
                continue;
            }
            sentences.addAll(chunkFixedWindow(sentence, MAX_SENTENCE_LENGTH, 0));
        }
        return sentences;
    }

    private List<String> chunkFixedWindow(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }

    private List<String> chunkByNaturalBreaks(String text, int chunkSize, int overlap) {
        String normalized = normalizeLineEndings(text).trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int hardEnd = Math.min(normalized.length(), start + chunkSize);
            int end = chooseNaturalBreak(normalized, start, hardEnd);
            chunks.add(normalized.substring(start, end).trim());
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }

    private int chooseNaturalBreak(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) {
            return text.length();
        }
        int minBreak = start + Math.max(80, (hardEnd - start) / 2);
        int paragraph = text.lastIndexOf("\n\n", hardEnd);
        if (paragraph >= minBreak) {
            return paragraph + 2;
        }
        for (int index = hardEnd - 1; index >= minBreak; index--) {
            char current = text.charAt(index);
            if (current == '\n' || current == '。' || current == '！' || current == '？'
                    || current == '.' || current == '!' || current == '?' || current == ';' || current == '；') {
                return index + 1;
            }
        }
        return hardEnd;
    }

    private boolean isCodeDeclaration(String trimmed) {
        if (trimmed == null || trimmed.isBlank()) {
            return false;
        }
        return TYPE_DECLARATION.matcher(trimmed).matches()
                || METHOD_DECLARATION.matcher(trimmed).matches()
                || FUNCTION_DECLARATION.matcher(trimmed).matches()
                || trimmed.startsWith("const ") && trimmed.contains("=>")
                || trimmed.startsWith("export function ")
                || trimmed.startsWith("export class ");
    }

    private boolean isPackageOrImport(String trimmed) {
        return trimmed.startsWith("package ")
                || trimmed.startsWith("import ")
                || trimmed.startsWith("using ")
                || trimmed.startsWith("#include")
                || trimmed.startsWith("from ") && trimmed.contains(" import ");
    }

    private boolean declarationEnded(String trimmed) {
        return trimmed.endsWith("}") || trimmed.endsWith("};") || trimmed.endsWith("end");
    }

    private void flush(List<String> chunks, StringBuilder current) {
        addChunk(chunks, current.toString());
        current.setLength(0);
    }

    private void addChunk(List<String> chunks, String chunk) {
        String normalized = chunk == null ? "" : chunk.trim();
        if (!normalized.isBlank()) {
            chunks.add(normalized);
        }
    }

    private String overlapPrefix(String previous, int overlap) {
        if (previous == null || previous.length() <= overlap || overlap <= 0) {
            return "";
        }
        int start = Math.max(0, previous.length() - overlap);
        int lineStart = previous.indexOf('\n', start);
        return previous.substring(lineStart >= 0 ? lineStart + 1 : start).trim() + '\n';
    }

    private int braceDelta(String line) {
        int delta = 0;
        boolean inString = false;
        char stringQuote = 0;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if ((current == '"' || current == '\'') && (index == 0 || line.charAt(index - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    stringQuote = current;
                } else if (stringQuote == current) {
                    inString = false;
                }
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                delta++;
            } else if (current == '}') {
                delta--;
            }
        }
        return delta;
    }

    private boolean looksLikeCode(String text) {
        String sample = text.length() > 2000 ? text.substring(0, 2000) : text;
        String lower = sample.toLowerCase(Locale.ROOT);
        return lower.contains("class ")
                || lower.contains("public ")
                || lower.contains("private ")
                || lower.contains("function ")
                || lower.contains("def ")
                || lower.contains("return ")
                || lower.contains("import ")
                || lower.contains("@@ ")
                || sample.contains("{")
                || sample.contains("->")
                || sample.contains("=>");
    }

    private String normalizeLineEndings(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
