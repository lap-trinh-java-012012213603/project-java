package com.analoghub.platform.search.listener;

import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.search.document.LabSearchDocument;
import com.analoghub.platform.search.repository.LabSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class LabIndexSyncListener {

    private final LabSearchRepository labSearchRepository;

    @ApplicationModuleListener
    public void onFilmLabCreated(FilmLabCreatedEvent event) {
        if (event == null || event.labId() == null) {
            return;
        }
        String labIdStr = event.labId().toString();
        LabSearchDocument doc = labSearchRepository.findById(labIdStr)
                .orElseGet(() -> LabSearchDocument.builder()
                        .labId(labIdStr)
                        .status("ACTIVE")
                        .isAcceptingOrders(true)
                        .avgRating(5.0f)
                        .reviewCount(0)
                        .build());

        doc.setName(event.labName());
        if (doc.getSlug() == null && event.labName() != null) {
            doc.setSlug(toSlug(event.labName()));
        }
        doc.setUpdatedAt(Instant.now());

        labSearchRepository.save(doc);
        log.info("Synced FilmLab document to Elasticsearch: labId={}, name={}", labIdStr, event.labName());
    }

    static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}

