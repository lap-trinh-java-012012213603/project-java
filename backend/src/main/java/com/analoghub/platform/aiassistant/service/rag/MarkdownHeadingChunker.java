package com.analoghub.platform.aiassistant.service.rag;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

@Component
public class MarkdownHeadingChunker {

    public static final int TARGET_MIN_TOKENS = 350;
    public static final int TARGET_MAX_TOKENS = 512;
    public static final int OVERLAP_TOKENS = 64;
    public static final double TOKENS_PER_WORD_VI = 1.35;

    public record ChunkRaw(
            int chunkIndex,
            String chunkTitle,
            String contextPrefix,
            String chunkContent,
            String contentSha256,
            int estimatedTokens
    ) {}

    private record HeadingEntry(int level, String title) {}

    public List<ChunkRaw> split(String markdown, String documentTitle) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        String normalizedDocTitle = (documentTitle != null && !documentTitle.isBlank())
                ? documentTitle.trim()
                : extractFirstH1OrFallback(markdown);

        List<ChunkRaw> result = new ArrayList<>();
        String[] lines = markdown.split("\\r?\\n");

        Deque<HeadingEntry> headingHierarchy = new ArrayDeque<>();
        StringBuilder currentBuffer = new StringBuilder();
        String activeBreadcrumb = null;
        boolean hasNewContent = false;
        int chunkIdx = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                int level = countHeadingLevel(trimmed);
                String titleText = trimmed.replaceAll("^#+\\s*", "").trim();

                if (hasNewContent && estimateTokens(currentBuffer.toString()) >= TARGET_MIN_TOKENS) {
                    String rawChunkBody = currentBuffer.toString();
                    String breadcrumbToUse = activeBreadcrumb != null
                            ? activeBreadcrumb
                            : buildBreadcrumb(normalizedDocTitle, headingHierarchy);
                    result.add(buildChunkWithBreadcrumb(chunkIdx++, breadcrumbToUse, rawChunkBody));
                    currentBuffer = new StringBuilder(extractOverlap(rawChunkBody, OVERLAP_TOKENS));
                    hasNewContent = false;
                    activeBreadcrumb = null;
                }

                if (!titleText.isEmpty()) {
                    updateHierarchy(headingHierarchy, level, titleText);
                }
            } else if (trimmed.isEmpty()) {
                if (hasNewContent && currentBuffer.length() > 0 && !currentBuffer.toString().endsWith("\n\n")) {
                    currentBuffer.append("\n");
                }
            } else {
                List<String> segments = splitIntoSentenceSegments(line);
                for (String segment : segments) {
                    String candidate = currentBuffer.length() == 0
                            ? segment
                            : currentBuffer + " " + segment;

                    int currentTokens = estimateTokens(currentBuffer.toString());
                    int candidateTokens = estimateTokens(candidate);

                    if (hasNewContent && currentTokens >= TARGET_MIN_TOKENS && candidateTokens > TARGET_MAX_TOKENS) {
                        String rawChunkBody = currentBuffer.toString();
                        String breadcrumbToUse = activeBreadcrumb != null
                                ? activeBreadcrumb
                                : buildBreadcrumb(normalizedDocTitle, headingHierarchy);
                        result.add(buildChunkWithBreadcrumb(chunkIdx++, breadcrumbToUse, rawChunkBody));
                        currentBuffer = new StringBuilder(extractOverlap(rawChunkBody, OVERLAP_TOKENS));
                        hasNewContent = false;
                        activeBreadcrumb = null;
                    }

                    if (!hasNewContent) {
                        activeBreadcrumb = buildBreadcrumb(normalizedDocTitle, headingHierarchy);
                    }

                    if (currentBuffer.length() > 0 && !currentBuffer.toString().endsWith("\n")
                            && !currentBuffer.toString().endsWith(" ")) {
                        currentBuffer.append(" ");
                    }
                    currentBuffer.append(segment);
                    hasNewContent = true;

                    if (estimateTokens(currentBuffer.toString()) >= TARGET_MAX_TOKENS) {
                        String rawChunkBody = currentBuffer.toString();
                        String breadcrumbToUse = activeBreadcrumb != null
                                ? activeBreadcrumb
                                : buildBreadcrumb(normalizedDocTitle, headingHierarchy);
                        result.add(buildChunkWithBreadcrumb(chunkIdx++, breadcrumbToUse, rawChunkBody));
                        currentBuffer = new StringBuilder(extractOverlap(rawChunkBody, OVERLAP_TOKENS));
                        hasNewContent = false;
                        activeBreadcrumb = null;
                    }
                }
                if (hasNewContent) {
                    currentBuffer.append("\n");
                }
            }
        }

        if (hasNewContent && !currentBuffer.toString().isBlank()) {
            String breadcrumbToUse = activeBreadcrumb != null
                    ? activeBreadcrumb
                    : buildBreadcrumb(normalizedDocTitle, headingHierarchy);
            result.add(buildChunkWithBreadcrumb(chunkIdx, breadcrumbToUse, currentBuffer.toString()));
        }

        return result;
    }

    private ChunkRaw buildChunkWithBreadcrumb(int index, String breadcrumb, String content) {
        String contextPrefix = "[" + breadcrumb + "]";
        String cleanBody = content.trim();
        String fullContentWithPrefix = contextPrefix + "\n" + cleanBody;
        String sha256 = calculateSha256(fullContentWithPrefix);
        int tokens = estimateTokens(fullContentWithPrefix);
        return new ChunkRaw(index, breadcrumb, contextPrefix, fullContentWithPrefix, sha256, tokens);
    }

    String buildBreadcrumb(String docTitle, Deque<HeadingEntry> hierarchy) {
        List<String> parts = new ArrayList<>();
        if (docTitle != null && !docTitle.isBlank()) {
            parts.add(docTitle.trim());
        }
        for (HeadingEntry entry : hierarchy) {
            if (parts.isEmpty() || !parts.get(parts.size() - 1).equalsIgnoreCase(entry.title())) {
                if (parts.size() == 1 && entry.level() == 1 && parts.get(0).equalsIgnoreCase(entry.title())) {
                    continue;
                }
                parts.add(entry.title());
            }
        }
        return parts.isEmpty() ? "Tài liệu Kỹ thuật Buồng tối" : String.join(" > ", parts);
    }

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return (int) (words.length * TOKENS_PER_WORD_VI);
    }

    public String extractOverlap(String text, int overlapTokens) {
        if (text == null || text.isBlank() || overlapTokens <= 0) {
            return "";
        }
        String[] words = text.trim().split("\\s+");
        int wordsToKeep = (int) (overlapTokens / TOKENS_PER_WORD_VI);
        if (wordsToKeep <= 0 || words.length <= wordsToKeep) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(words, words.length - wordsToKeep, words.length)) + "\n";
    }

    private List<String> splitIntoSentenceSegments(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        int maxWordsPerSegment = (int) (TARGET_MAX_TOKENS / TOKENS_PER_WORD_VI) - 20;
        String[] sentences = trimmed.split("(?<=[.!?])\\s+");
        List<String> segments = new ArrayList<>();
        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.isEmpty()) {
                continue;
            }
            String[] words = s.split("\\s+");
            if (words.length <= maxWordsPerSegment) {
                segments.add(s);
            } else {
                for (int i = 0; i < words.length; i += maxWordsPerSegment) {
                    int end = Math.min(words.length, i + maxWordsPerSegment);
                    segments.add(String.join(" ", Arrays.copyOfRange(words, i, end)));
                }
            }
        }
        return segments;
    }

    private String extractFirstH1OrFallback(String markdown) {
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.startsWith("##")) {
                String h1 = trimmed.replaceFirst("^#+\\s*", "").trim();
                if (!h1.isEmpty()) {
                    return h1;
                }
            }
        }
        return "Tài liệu Kỹ thuật Buồng tối";
    }

    private int countHeadingLevel(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') {
            count++;
        }
        return Math.min(count, 6);
    }

    private void updateHierarchy(Deque<HeadingEntry> hierarchy, int level, String title) {
        while (!hierarchy.isEmpty() && hierarchy.peekLast().level() >= level) {
            hierarchy.pollLast();
        }
        hierarchy.addLast(new HeadingEntry(level, title));
    }

    public String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
