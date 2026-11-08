package com.analoghub.platform.aiassistant.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class KnowledgeSeedLoader implements ApplicationRunner {

    public static final String SOURCE_TYPE_SEED_DOC = "SEED_DOC";
    public static final String SEED_RESOURCE_PATTERN = "classpath*:knowledge-seed/*.md";

    public static final List<String> DEFAULT_SEED_FILES = List.of(
            "01-c41-development-process.md",
            "02-ecn2-motion-picture-film.md",
            "03-black-and-white-chemistry.md",
            "04-e6-color-reversal-slide.md",
            "05-push-pull-exposure-guide.md",
            "06-film-scanner-noritsu-vs-frontier.md",
            "07-negative-storage-and-preservation.md",
            "08-darkroom-chemical-safety.md"
    );

    public record SeedDocument(
            String seedCode,
            String fileName,
            String articleSlug,
            String sourceType,
            UUID sourceRefId,
            String title,
            String fileSha256,
            Map<String, Object> metadata,
            List<MarkdownHeadingChunker.ChunkRaw> chunks
    ) {}

    @FunctionalInterface
    public interface SeedDocumentConsumer {
        void accept(SeedDocument seedDocument);
    }

    private final MarkdownHeadingChunker chunker;
    private final ObjectProvider<SeedDocumentConsumer> seedDocumentConsumerProvider;

    private final Map<String, String> loadedFileChecksums = new ConcurrentHashMap<>();
    private final Map<String, SeedDocument> loadedDocuments = new LinkedHashMap<>();

    @Value("${app.ai.rag.seed-loader-enabled:true}")
    private boolean seedLoaderEnabled = true;

    @Autowired
    public KnowledgeSeedLoader(
            MarkdownHeadingChunker chunker,
            ObjectProvider<SeedDocumentConsumer> seedDocumentConsumerProvider
    ) {
        this.chunker = chunker;
        this.seedDocumentConsumerProvider = seedDocumentConsumerProvider;
    }

    public KnowledgeSeedLoader(MarkdownHeadingChunker chunker) {
        this(chunker, null);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedLoaderEnabled) {
            log.info("Knowledge seed loader is disabled (app.ai.rag.seed-loader-enabled=false).");
            return;
        }
        try {
            List<SeedDocument> newlyLoaded = loadSeedDocuments();
            int totalChunks = newlyLoaded.stream().mapToInt(doc -> doc.chunks().size()).sum();
            log.info("KnowledgeSeedLoader completed: loaded {} seed documents ({} total chunks).",
                    newlyLoaded.size(), totalChunks);
        } catch (Exception ex) {
            log.warn("Failed to bootstrap knowledge seed documents at startup: {}", ex.getMessage());
        }
    }

    public synchronized List<SeedDocument> loadSeedDocuments() {
        List<Resource> resources = resolveSeedResources();
        List<SeedDocument> newlyProcessed = new ArrayList<>();

        for (Resource resource : resources) {
            String fileName = resource.getFilename();
            if (fileName == null || !fileName.endsWith(".md")) {
                continue;
            }
            String markdownContent = readResourceUtf8(resource);
            Optional<SeedDocument> processed = processSeedFileIfChanged(fileName, markdownContent);
            processed.ifPresent(newlyProcessed::add);
        }

        return Collections.unmodifiableList(newlyProcessed);
    }

    public synchronized Optional<SeedDocument> processSeedFileIfChanged(String fileName, String markdownContent) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return Optional.empty();
        }

        String fileSha256 = chunker.calculateSha256(markdownContent.trim());
        String existingChecksum = loadedFileChecksums.get(fileName);
        if (fileSha256.equals(existingChecksum)) {
            log.debug("Knowledge seed '{}' checksum unchanged ({}); skipping re-ingestion.", fileName, fileSha256);
            return Optional.empty();
        }

        String seedCode = resolveSeedCode(fileName);
        String slug = resolveSlug(fileName);
        UUID sourceRefId = UUID.nameUUIDFromBytes((SOURCE_TYPE_SEED_DOC + ":" + fileName).getBytes(StandardCharsets.UTF_8));
        String title = extractDocumentTitle(markdownContent, slug);
        Map<String, Object> metadata = buildSeedMetadata(fileName, seedCode, slug, fileSha256);

        List<MarkdownHeadingChunker.ChunkRaw> chunks = chunker.split(markdownContent, title);
        SeedDocument seedDocument = new SeedDocument(
                seedCode,
                fileName,
                slug,
                SOURCE_TYPE_SEED_DOC,
                sourceRefId,
                title,
                fileSha256,
                metadata,
                chunks
        );

        loadedFileChecksums.put(fileName, fileSha256);
        loadedDocuments.put(fileName, seedDocument);

        if (seedDocumentConsumerProvider != null) {
            SeedDocumentConsumer consumer = seedDocumentConsumerProvider.getIfAvailable();
            if (consumer != null) {
                consumer.accept(seedDocument);
            }
        }

        return Optional.of(seedDocument);
    }

    public synchronized List<SeedDocument> getLoadedDocuments() {
        return List.copyOf(loadedDocuments.values());
    }

    public synchronized Optional<SeedDocument> getLoadedDocument(String fileName) {
        return Optional.ofNullable(loadedDocuments.get(fileName));
    }

    public boolean isChecksumUnchanged(String fileName, String fileSha256) {
        return fileSha256 != null && fileSha256.equals(loadedFileChecksums.get(fileName));
    }

    public synchronized void clearLoadedCache() {
        loadedFileChecksums.clear();
        loadedDocuments.clear();
    }

    List<Resource> resolveSeedResources() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] scanned = resolver.getResources(SEED_RESOURCE_PATTERN);
            List<Resource> valid = Arrays.stream(scanned)
                    .filter(Resource::exists)
                    .filter(r -> r.getFilename() != null && r.getFilename().endsWith(".md"))
                    .sorted(Comparator.comparing(r -> r.getFilename() != null ? r.getFilename() : ""))
                    .toList();
            if (!valid.isEmpty()) {
                return valid;
            }
        } catch (IOException ex) {
            log.debug("Pattern scan for '{}' failed, falling back to default seed list: {}",
                    SEED_RESOURCE_PATTERN, ex.getMessage());
        }

        List<Resource> fallback = new ArrayList<>();
        for (String fileName : DEFAULT_SEED_FILES) {
            ClassPathResource resource = new ClassPathResource("knowledge-seed/" + fileName);
            if (resource.exists()) {
                fallback.add(resource);
            }
        }
        return fallback;
    }

    private String readResourceUtf8(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read knowledge seed resource: " + resource.getFilename(), ex);
        }
    }

    static String extractDocumentTitle(String markdown, String fallbackTitle) {
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.startsWith("##")) {
                String title = trimmed.replaceFirst("^#+\\s*", "").trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
        }
        return fallbackTitle;
    }

    static String resolveSeedCode(String fileName) {
        if (fileName != null && fileName.length() >= 2 && Character.isDigit(fileName.charAt(0))
                && Character.isDigit(fileName.charAt(1))) {
            return "SEED-" + fileName.substring(0, 2);
        }
        return "SEED-00";
    }

    static String resolveSlug(String fileName) {
        if (fileName == null) {
            return "unknown-seed";
        }
        String withoutExt = fileName.endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3)
                : fileName;
        return withoutExt.replaceFirst("^\\d+-", "");
    }

    static Map<String, Object> buildSeedMetadata(
            String fileName,
            String seedCode,
            String slug,
            String fileSha256
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "SEED");
        metadata.put("seedCode", seedCode);
        metadata.put("articleSlug", slug);
        metadata.put("fileName", fileName);
        metadata.put("fileSha256", fileSha256);

        if (fileName.startsWith("01-")) {
            metadata.put("category", "DEV_PROCESS");
            metadata.put("processType", "C41");
        } else if (fileName.startsWith("02-")) {
            metadata.put("category", "DEV_PROCESS");
            metadata.put("processType", "ECN2");
        } else if (fileName.startsWith("03-")) {
            metadata.put("category", "DEV_PROCESS");
            metadata.put("processType", "BW");
        } else if (fileName.startsWith("04-")) {
            metadata.put("category", "DEV_PROCESS");
            metadata.put("processType", "E6");
        } else if (fileName.startsWith("05-")) {
            metadata.put("category", "TECHNIQUE");
            metadata.put("topics", List.of("EXPOSURE", "PUSH_PULL"));
        } else if (fileName.startsWith("06-")) {
            metadata.put("category", "SCANNER");
            metadata.put("models", List.of("NORITSU", "FRONTIER"));
        } else if (fileName.startsWith("07-")) {
            metadata.put("category", "ARCHIVE");
            metadata.put("topics", List.of("PRESERVATION"));
        } else if (fileName.startsWith("08-")) {
            metadata.put("category", "SAFETY");
            metadata.put("topics", List.of("VENTILATION", "CHEMICALS"));
        } else {
            metadata.put("category", "DARKROOM_TECHNIQUE");
        }

        return Collections.unmodifiableMap(metadata);
    }
}
