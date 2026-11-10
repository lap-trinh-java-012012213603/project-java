package com.analoghub.platform.aiassistant.listener;

import com.analoghub.platform.aiassistant.service.rag.RagIngestionService;
import com.analoghub.platform.community.event.ArticleApprovedForRagEvent;
import com.analoghub.platform.community.event.ArticleRetractedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagKnowledgeSyncListener {

    private final RagIngestionService ragIngestionService;

    @ApplicationModuleListener
    public void onArticleApprovedForRag(ArticleApprovedForRagEvent event) {
        if (event == null || event.articleId() == null) {
            return;
        }
        log.info("Syncing approved expert article to RAG knowledge store: articleId={}, slug={}",
                event.articleId(), event.slug());
        ragIngestionService.ingestArticleContent(
                event.articleId(),
                event.slug(),
                event.title(),
                event.category(),
                event.markdownContent()
        );
    }

    @ApplicationModuleListener
    public void onArticleRetracted(ArticleRetractedEvent event) {
        if (event == null || event.articleId() == null) {
            return;
        }
        log.info("Removing retracted expert article from RAG knowledge store: articleId={}, slug={}",
                event.articleId(), event.slug());
        ragIngestionService.retractArticleContent(event.articleId());
    }
}
