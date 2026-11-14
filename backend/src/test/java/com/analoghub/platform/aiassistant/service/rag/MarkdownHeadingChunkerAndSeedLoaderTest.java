package com.analoghub.platform.aiassistant.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownHeadingChunkerAndSeedLoaderTest {

    private MarkdownHeadingChunker chunker;
    private KnowledgeSeedLoader seedLoader;

    @BeforeEach
    void setUp() {
        chunker = new MarkdownHeadingChunker();
        seedLoader = new KnowledgeSeedLoader(chunker);
    }

    @Test
    void split_preservesBreadcrumbHierarchyAndComputesSha256AndOverlap() {
        StringBuilder section1 = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            section1.append("Dung dịch Developer của quy trình C-41 bắt buộc duy trì ở nhiệt độ 37.8°C (100°F) ")
                    .append("với dung sai không vượt quá ±0.2°C trong suốt thời gian 3 phút 15 giây. ");
        }

        StringBuilder section2 = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            section2.append("Bước Bleach sử dụng phức chất Ferric Ammonium EDTA có sục khí oxy để chuyển bạc kim loại ")
                    .append("thành muối bạc hòa tan trước khi đưa sang bể Fixer định hình. ");
        }

        String markdown = """
                # Quy trình tráng C-41
                ## Hóa chất First Developer
                ### Kiểm soát Nhiệt độ
                %s
                ## Quy trình Tẩy bạc Bleach và Định hình Fixer
                ### Tách riêng Bleach và Fixer
                %s
                """.formatted(section1, section2);

        List<MarkdownHeadingChunker.ChunkRaw> chunks = chunker.split(markdown, "Quy trình tráng C-41");

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);

        MarkdownHeadingChunker.ChunkRaw firstChunk = chunks.getFirst();
        assertThat(firstChunk.chunkIndex()).isZero();
        assertThat(firstChunk.chunkTitle())
                .isEqualTo("Quy trình tráng C-41 > Hóa chất First Developer > Kiểm soát Nhiệt độ");
        assertThat(firstChunk.contextPrefix())
                .isEqualTo("[Quy trình tráng C-41 > Hóa chất First Developer > Kiểm soát Nhiệt độ]");
        assertThat(firstChunk.chunkContent())
                .startsWith("[Quy trình tráng C-41 > Hóa chất First Developer > Kiểm soát Nhiệt độ]\n")
                .contains("37.8°C (100°F)");
        assertThat(firstChunk.contentSha256()).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(firstChunk.estimatedTokens())
                .isBetween(MarkdownHeadingChunker.TARGET_MIN_TOKENS, MarkdownHeadingChunker.TARGET_MAX_TOKENS + 60);

        MarkdownHeadingChunker.ChunkRaw lastChunk = chunks.getLast();
        assertThat(lastChunk.chunkTitle())
                .isEqualTo("Quy trình tráng C-41 > Quy trình Tẩy bạc Bleach và Định hình Fixer > Tách riêng Bleach và Fixer");
        assertThat(lastChunk.chunkContent()).contains("Ferric Ammonium EDTA");
    }

    @Test
    void loadSeedDocuments_loadsAll8DarkroomSeedFilesWithCorrectMetadataAndDeduplicatesByChecksum() {
        List<KnowledgeSeedLoader.SeedDocument> loadedConsumerDocs = new ArrayList<>();
        ObjectProvider<KnowledgeSeedLoader.SeedDocumentConsumer> provider = new ObjectProvider<>() {
            @Override
            public KnowledgeSeedLoader.SeedDocumentConsumer getObject(Object... args) throws BeansException {
                return loadedConsumerDocs::add;
            }

            @Override
            public KnowledgeSeedLoader.SeedDocumentConsumer getIfAvailable() throws BeansException {
                return loadedConsumerDocs::add;
            }

            @Override
            public KnowledgeSeedLoader.SeedDocumentConsumer getIfUnique() throws BeansException {
                return loadedConsumerDocs::add;
            }

            @Override
            public KnowledgeSeedLoader.SeedDocumentConsumer getObject() throws BeansException {
                return loadedConsumerDocs::add;
            }
        };

        KnowledgeSeedLoader loaderWithConsumer = new KnowledgeSeedLoader(chunker, provider);

        List<KnowledgeSeedLoader.SeedDocument> firstRun = loaderWithConsumer.loadSeedDocuments();

        assertThat(firstRun).hasSize(8);
        assertThat(loadedConsumerDocs).hasSize(8);

        assertThat(firstRun)
                .extracting(KnowledgeSeedLoader.SeedDocument::seedCode)
                .containsExactly("SEED-01", "SEED-02", "SEED-03", "SEED-04", "SEED-05", "SEED-06", "SEED-07", "SEED-08");

        assertThat(firstRun)
                .extracting(KnowledgeSeedLoader.SeedDocument::fileName)
                .containsExactlyElementsOf(KnowledgeSeedLoader.DEFAULT_SEED_FILES);

        KnowledgeSeedLoader.SeedDocument c41Doc = firstRun.get(0);
        assertThat(c41Doc.sourceType()).isEqualTo(KnowledgeSeedLoader.SOURCE_TYPE_SEED_DOC);
        assertThat(c41Doc.metadata())
                .containsEntry("source", "SEED")
                .containsEntry("category", "DEV_PROCESS")
                .containsEntry("processType", "C41");
        assertThat(c41Doc.chunks()).isNotEmpty();
        assertThat(c41Doc.chunks().getFirst().chunkTitle())
                .contains("Quy trình tráng C-41 > Hóa chất First Developer > Kiểm soát Nhiệt độ");

        for (KnowledgeSeedLoader.SeedDocument doc : firstRun) {
            assertThat(doc.fileSha256()).hasSize(64);
            assertThat(doc.chunks()).hasSizeGreaterThanOrEqualTo(2);
            for (MarkdownHeadingChunker.ChunkRaw chunk : doc.chunks()) {
                assertThat(chunk.chunkContent()).startsWith(chunk.contextPrefix() + "\n");
                assertThat(chunk.contentSha256()).hasSize(64);
                assertThat(chunk.estimatedTokens()).isGreaterThan(50);
            }
        }

        // Second run: checksums are unchanged, so 0 files should be re-ingested
        List<KnowledgeSeedLoader.SeedDocument> secondRun = loaderWithConsumer.loadSeedDocuments();
        assertThat(secondRun).isEmpty();
        assertThat(loaderWithConsumer.getLoadedDocuments()).hasSize(8);
    }
}
