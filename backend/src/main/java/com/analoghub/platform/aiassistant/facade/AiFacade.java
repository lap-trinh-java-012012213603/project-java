package com.analoghub.platform.aiassistant.facade;

import java.util.UUID;

public interface AiFacade {
    // Vector HNSW Knowledge methods
    void ingestKnowledgeChunk(String content);
    
    // AI Chat & Moderation
    String askAssistant(UUID userId, UUID sessionId, String prompt);
    boolean checkContentSafety(String text);
    
    // Scan QC Pipeline
    void analyzeScanQuality(UUID frameId, String s3Url);
}
