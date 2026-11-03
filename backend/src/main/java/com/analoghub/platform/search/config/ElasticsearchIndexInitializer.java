package com.analoghub.platform.search.config;

import com.analoghub.platform.search.document.LabSearchDocument;
import com.analoghub.platform.search.document.ListingSearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    public static final String LAB_INDEX_NAME = LabSearchDocument.INDEX_NAME;
    public static final String LISTING_INDEX_NAME = ListingSearchDocument.INDEX_NAME;

    public static final String LAB_MAPPING_RESOURCE = "elasticsearch/lab-index-mapping.json";
    public static final String LISTING_MAPPING_RESOURCE = "elasticsearch/listing-index-mapping.json";

    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${app.search.index-init-enabled:true}")
    private boolean indexInitEnabled = true;

    @Override
    public void run(ApplicationArguments args) {
        if (!indexInitEnabled) {
            log.info("Elasticsearch index initialization is disabled (app.search.index-init-enabled=false).");
            return;
        }
        try {
            initializeIndices();
        } catch (Exception ex) {
            log.warn("Could not initialize Elasticsearch indices at startup (Elasticsearch cluster may be offline): {}",
                    ex.getMessage());
        }
    }

    public void initializeIndices() {
        ensureIndexExists(LAB_INDEX_NAME, LAB_MAPPING_RESOURCE);
        ensureIndexExists(LISTING_INDEX_NAME, LISTING_MAPPING_RESOURCE);
    }

    @SuppressWarnings("unchecked")
    public boolean ensureIndexExists(String indexName, String classpathResourcePath) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
        if (indexOps.exists()) {
            log.debug("Elasticsearch index '{}' already exists; skipping creation.", indexName);
            return false;
        }

        String rawJson = loadMappingResource(classpathResourcePath);
        Document rootDocument = Document.parse(rawJson);

        Object rawSettings = rootDocument.get("settings");
        Object rawMappings = rootDocument.get("mappings");

        Document settings = rawSettings instanceof Map<?, ?> settingsMap
                ? Document.from((Map<String, Object>) settingsMap)
                : Document.create();

        Document mappings = rawMappings instanceof Map<?, ?> mappingsMap
                ? Document.from((Map<String, Object>) mappingsMap)
                : Document.create();

        boolean created = indexOps.create(settings, mappings);
        if (created) {
            log.info("Created Elasticsearch index '{}' from '{}'.", indexName, classpathResourcePath);
        } else {
            log.warn("Elasticsearch index '{}' creation returned false.", indexName);
        }
        return created;
    }

    String loadMappingResource(String classpathResourcePath) {
        ClassPathResource resource = new ClassPathResource(classpathResourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to read Elasticsearch index mapping resource: " + classpathResourcePath, ex);
        }
    }
}

