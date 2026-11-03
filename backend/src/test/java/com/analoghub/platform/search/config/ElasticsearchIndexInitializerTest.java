package com.analoghub.platform.search.config;

import com.analoghub.platform.search.document.LabSearchDocument;
import com.analoghub.platform.search.document.ListingSearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchIndexInitializerTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations labIndexOps;

    @Mock
    private IndexOperations listingIndexOps;

    private ElasticsearchIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new ElasticsearchIndexInitializer(elasticsearchOperations);
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializeIndices_whenIndicesDoNotExist_createsBothIndicesWithIcuFoldingAndEdgeNgram() {
        when(elasticsearchOperations.indexOps(IndexCoordinates.of(LabSearchDocument.INDEX_NAME)))
                .thenReturn(labIndexOps);
        when(elasticsearchOperations.indexOps(IndexCoordinates.of(ListingSearchDocument.INDEX_NAME)))
                .thenReturn(listingIndexOps);

        when(labIndexOps.exists()).thenReturn(false);
        when(listingIndexOps.exists()).thenReturn(false);
        when(labIndexOps.create(any(Document.class), any(Document.class))).thenReturn(true);
        when(listingIndexOps.create(any(Document.class), any(Document.class))).thenReturn(true);

        initializer.initializeIndices();

        ArgumentCaptor<Document> labSettingsCaptor = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<Document> labMappingsCaptor = ArgumentCaptor.forClass(Document.class);
        verify(labIndexOps).create(labSettingsCaptor.capture(), labMappingsCaptor.capture());

        Document labSettings = labSettingsCaptor.getValue();
        Document labMappings = labMappingsCaptor.getValue();
        assertThat(labSettings).containsKey("analysis");

        Map<String, Object> labProperties = (Map<String, Object>) labMappings.get("properties");
        assertThat(labProperties)
                .containsKeys("labId", "slug", "name", "location", "supportedFormats", "supportedProcesses", "minPriceVnd");

        ArgumentCaptor<Document> listingSettingsCaptor = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<Document> listingMappingsCaptor = ArgumentCaptor.forClass(Document.class);
        verify(listingIndexOps).create(listingSettingsCaptor.capture(), listingMappingsCaptor.capture());

        Document listingMappings = listingMappingsCaptor.getValue();
        Map<String, Object> listingProperties = (Map<String, Object>) listingMappings.get("properties");
        assertThat(listingProperties)
                .containsKeys("listingId", "sellerUserId", "title", "category", "brand", "conditionGrade", "priceVnd");
    }

    @Test
    void initializeIndices_whenIndicesAlreadyExist_skipsCreation() {
        when(elasticsearchOperations.indexOps(IndexCoordinates.of(LabSearchDocument.INDEX_NAME)))
                .thenReturn(labIndexOps);
        when(elasticsearchOperations.indexOps(IndexCoordinates.of(ListingSearchDocument.INDEX_NAME)))
                .thenReturn(listingIndexOps);

        when(labIndexOps.exists()).thenReturn(true);
        when(listingIndexOps.exists()).thenReturn(true);

        initializer.initializeIndices();

        verify(labIndexOps, never()).create(any(Document.class), any(Document.class));
        verify(listingIndexOps, never()).create(any(Document.class), any(Document.class));
    }
}

