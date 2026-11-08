package com.analoghub.platform.aiassistant.service.rag;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.LlmMessage;
import com.analoghub.platform.aiassistant.client.LlmPromptRequest;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.StructuredLlmResponse;
import com.analoghub.platform.aiassistant.client.TokenUsage;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.AiChatMessage;
import com.analoghub.platform.aiassistant.domain.AiChatSession;
import com.analoghub.platform.aiassistant.dto.AiChatHistoryResponse;
import com.analoghub.platform.aiassistant.dto.AiChatHistoryResponse.AiChatMessageDto;
import com.analoghub.platform.aiassistant.dto.AiChatSessionListResponse;
import com.analoghub.platform.aiassistant.dto.AiChatSessionListResponse.AiChatSessionSummaryDto;
import com.analoghub.platform.aiassistant.dto.AiChatSessionListResponse.PaginationMeta;
import com.analoghub.platform.aiassistant.dto.AiChatSessionResponse;
import com.analoghub.platform.aiassistant.dto.AiChatSessionResponse.ContextFilmStockDto;
import com.analoghub.platform.aiassistant.dto.CreateAiChatSessionRequest;
import com.analoghub.platform.aiassistant.dto.RagCitationDto;
import com.analoghub.platform.aiassistant.dto.RagStructuredOutput;
import com.analoghub.platform.aiassistant.dto.RagStructuredOutput.RawCitationItem;
import com.analoghub.platform.aiassistant.dto.SendAiChatMessageRequest;
import com.analoghub.platform.aiassistant.dto.SendAiChatMessageResponse;
import com.analoghub.platform.aiassistant.dto.SendAiChatMessageResponse.RetrievalTelemetryDto;
import com.analoghub.platform.aiassistant.dto.SendAiChatMessageResponse.UsageTelemetryDto;
import com.analoghub.platform.aiassistant.repository.AiChatMessageRepository;
import com.analoghub.platform.aiassistant.repository.AiChatSessionRepository;
import com.analoghub.platform.aiassistant.service.governance.AiCostEstimator;
import com.analoghub.platform.aiassistant.service.guardrail.InputGuardrail;
import com.analoghub.platform.aiassistant.service.guardrail.InputGuardrail.InputGuardrailResult;
import com.analoghub.platform.aiassistant.service.guardrail.OutputGuardrail;
import com.analoghub.platform.aiassistant.service.guardrail.OutputGuardrail.OutputGuardrailResult;
import com.analoghub.platform.aiassistant.service.rag.CitationVerifier.VerificationResult;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService.RetrievalQuery;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService.RetrievalResult;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService.ScoredChunk;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RagChatService {

    public static final String PROMPT_TEMPLATE_PATH = "prompts/rag/rag-system-prompt-v1.0.st";
    public static final String PROMPT_KEY_RAG_CHAT = "rag-system-prompt";
    public static final String PROMPT_VERSION = "rag-v1.0";
    public static final int MAX_HISTORY_MESSAGES = 6;
    public static final int MAX_PREVIEW_LENGTH = 120;

    private static final String FALLBACK_SYSTEM_PROMPT_TEMPLATE = """
            Bạn là Trợ lý Nhiếp ảnh Phim Thông minh của nền tảng AnalogHub, chuyên gia về buồng tối, hóa chất tráng scan film và thiết bị analog tại Việt Nam.

            QUY TẮC BẮT BUỘC (KHÔNG ĐƯỢC VI PHẠM):
            1. CHỈ sử dụng thông tin được cung cấp trong phần [TÀI LIỆU THAM KHẢO] bên dưới. Tuyệt đối KHÔNG suy diễn hoặc dùng kiến thức ngoài tài liệu.
            2. Mọi khẳng định kỹ thuật (nhiệt độ, thời gian, tỉ lệ pha, thao tác) BẮT BUỘC phải đính kèm trích dẫn nguồn theo định dạng [Doc-X], trong đó X là chỉ số tài liệu.
            3. Nếu tài liệu không chứa câu trả lời, hãy trung thực thông báo không có dữ liệu và hướng dẫn người dùng tham khảo thợ kỹ thuật tại Lab.
            4. KHÔNG bao giờ khuyến khích xả hóa chất buồng tối (Fixer, Bleach, Developer) trực tiếp ra nguồn nước sinh hoạt.

            [TÀI LIỆU THAM KHẢO]:
            {CONTEXT_CHUNKS}

            LỊCH SỬ TRÒ CHUYỆN:
            {CHAT_HISTORY}

            CÂU HỎI HIỆN TẠI:
            {USER_QUESTION}
            """;

    private static final String STRUCTURED_RESPONSE_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "answer": { "type": "string" },
                "citations": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "chunkId": { "type": "string" },
                      "articleSlug": { "type": "string" },
                      "quote": { "type": "string" }
                    },
                    "required": ["chunkId", "articleSlug", "quote"]
                  }
                },
                "confidenceScore": { "type": "number" },
                "abstained": { "type": "boolean" }
              },
              "required": ["answer", "citations", "confidenceScore", "abstained"]
            }
            """;

    private static final Map<String, ContextFilmStockDto> STANDARD_FILM_STOCK_CATALOG = Map.ofEntries(
            Map.entry("KODAK_TRI_X_400", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_TRI_X_400".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_TRI_X_400")
                    .brand("Kodak")
                    .name("Kodak Professional Tri-X 400")
                    .iso(400)
                    .processType("BW")
                    .build()),
            Map.entry("KODAK_PORTRA_400", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_PORTRA_400".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_PORTRA_400")
                    .brand("Kodak")
                    .name("Kodak Professional Portra 400")
                    .iso(400)
                    .processType("C-41")
                    .build()),
            Map.entry("KODAK_PORTRA_800", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_PORTRA_800".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_PORTRA_800")
                    .brand("Kodak")
                    .name("Kodak Professional Portra 800")
                    .iso(800)
                    .processType("C-41")
                    .build()),
            Map.entry("KODAK_GOLD_200", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_GOLD_200".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_GOLD_200")
                    .brand("Kodak")
                    .name("Kodak Gold 200")
                    .iso(200)
                    .processType("C-41")
                    .build()),
            Map.entry("KODAK_ULTRAMAX_400", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_ULTRAMAX_400".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_ULTRAMAX_400")
                    .brand("Kodak")
                    .name("Kodak UltraMax 400")
                    .iso(400)
                    .processType("C-41")
                    .build()),
            Map.entry("KODAK_COLORPLUS_200", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_COLORPLUS_200".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_COLORPLUS_200")
                    .brand("Kodak")
                    .name("Kodak ColorPlus 200")
                    .iso(200)
                    .processType("C-41")
                    .build()),
            Map.entry("KODAK_VISION3_500T", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_VISION3_500T".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_VISION3_500T")
                    .brand("Kodak")
                    .name("Kodak Vision3 500T (5219)")
                    .iso(500)
                    .processType("ECN-2")
                    .build()),
            Map.entry("KODAK_VISION3_250D", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_VISION3_250D".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_VISION3_250D")
                    .brand("Kodak")
                    .name("Kodak Vision3 250D (5207)")
                    .iso(250)
                    .processType("ECN-2")
                    .build()),
            Map.entry("KODAK_EKTACHROME_E100", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:KODAK_EKTACHROME_E100".getBytes(StandardCharsets.UTF_8)))
                    .code("KODAK_EKTACHROME_E100")
                    .brand("Kodak")
                    .name("Kodak Professional Ektachrome E100")
                    .iso(100)
                    .processType("E-6")
                    .build()),
            Map.entry("CINESTILL_800T", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:CINESTILL_800T".getBytes(StandardCharsets.UTF_8)))
                    .code("CINESTILL_800T")
                    .brand("CineStill")
                    .name("CineStill 800Tungsten")
                    .iso(800)
                    .processType("C-41")
                    .build()),
            Map.entry("CINESTILL_400D", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:CINESTILL_400D".getBytes(StandardCharsets.UTF_8)))
                    .code("CINESTILL_400D")
                    .brand("CineStill")
                    .name("CineStill 400Dynamic")
                    .iso(400)
                    .processType("C-41")
                    .build()),
            Map.entry("FUJIFILM_C200", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:FUJIFILM_C200".getBytes(StandardCharsets.UTF_8)))
                    .code("FUJIFILM_C200")
                    .brand("Fujifilm")
                    .name("Fujifilm Fujicolor C200")
                    .iso(200)
                    .processType("C-41")
                    .build()),
            Map.entry("FUJIFILM_PRO_400H", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:FUJIFILM_PRO_400H".getBytes(StandardCharsets.UTF_8)))
                    .code("FUJIFILM_PRO_400H")
                    .brand("Fujifilm")
                    .name("Fujifilm Pro 400H")
                    .iso(400)
                    .processType("C-41")
                    .build()),
            Map.entry("FUJIFILM_VELVIA_50", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:FUJIFILM_VELVIA_50".getBytes(StandardCharsets.UTF_8)))
                    .code("FUJIFILM_VELVIA_50")
                    .brand("Fujifilm")
                    .name("Fujifilm Fujichrome Velvia 50")
                    .iso(50)
                    .processType("E-6")
                    .build()),
            Map.entry("FUJIFILM_PROVIA_100F", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:FUJIFILM_PROVIA_100F".getBytes(StandardCharsets.UTF_8)))
                    .code("FUJIFILM_PROVIA_100F")
                    .brand("Fujifilm")
                    .name("Fujifilm Fujichrome Provia 100F")
                    .iso(100)
                    .processType("E-6")
                    .build()),
            Map.entry("ILFORD_HP5_PLUS", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:ILFORD_HP5_PLUS".getBytes(StandardCharsets.UTF_8)))
                    .code("ILFORD_HP5_PLUS")
                    .brand("Ilford")
                    .name("Ilford HP5 Plus 400")
                    .iso(400)
                    .processType("BW")
                    .build()),
            Map.entry("ILFORD_DELTA_400", ContextFilmStockDto.builder()
                    .filmStockId(UUID.nameUUIDFromBytes("FILM:ILFORD_DELTA_400".getBytes(StandardCharsets.UTF_8)))
                    .code("ILFORD_DELTA_400")
                    .brand("Ilford")
                    .name("Ilford Delta 400 Professional")
                    .iso(400)
                    .processType("BW")
                    .build())
    );

    private record SessionMetadata(
            String title,
            ModelProvider activeModelProvider,
            String contextFilmStockCode
    ) {
    }

    private record MessageMetadata(
            List<RagCitationDto> citations,
            boolean safetyWarningAttached,
            boolean abstained,
            String abstentionReason,
            Integer promptTokens,
            Integer completionTokens,
            Long latencyMs,
            Double confidenceScore,
            String promptVersion
    ) {
    }

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final RagRetrievalService ragRetrievalService;
    private final AiModelRouter aiModelRouter;
    private final AiProperties aiProperties;
    private final AiCostEstimator aiCostEstimator;
    private final InputGuardrail inputGuardrail;
    private final OutputGuardrail outputGuardrail;
    private final CitationVerifier citationVerifier;
    private final String systemPromptTemplate;

    private final Map<UUID, SessionMetadata> sessionMetadataCache = new ConcurrentHashMap<>();
    private final Map<UUID, MessageMetadata> messageMetadataCache = new ConcurrentHashMap<>();

    @Autowired
    public RagChatService(
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            RagRetrievalService ragRetrievalService,
            AiModelRouter aiModelRouter,
            AiProperties aiProperties,
            AiCostEstimator aiCostEstimator,
            InputGuardrail inputGuardrail,
            OutputGuardrail outputGuardrail,
            CitationVerifier citationVerifier
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.ragRetrievalService = ragRetrievalService;
        this.aiModelRouter = aiModelRouter;
        this.aiProperties = aiProperties;
        this.aiCostEstimator = aiCostEstimator != null ? aiCostEstimator : new AiCostEstimator();
        this.inputGuardrail = inputGuardrail != null ? inputGuardrail : new InputGuardrail();
        this.outputGuardrail = outputGuardrail != null ? outputGuardrail : new OutputGuardrail();
        this.citationVerifier = citationVerifier != null ? citationVerifier : new CitationVerifier();
        this.systemPromptTemplate = loadSystemPromptTemplate();
    }

    /**
     * API 1: POST /api/v1/ai/chat/sessions
     */
    @Transactional
    public AiChatSessionResponse createSession(UUID userId, CreateAiChatSessionRequest request) {
        ModelProvider activeProvider = request != null && request.preferredProvider() != null
                ? request.preferredProvider()
                : aiProperties.resolvePrimaryProvider();

        String filmStockCode = request != null && StringUtils.hasText(request.contextFilmStockCode())
                ? request.contextFilmStockCode().trim().toUpperCase(Locale.ROOT)
                : null;
        ContextFilmStockDto contextFilmStock = null;
        if (filmStockCode != null) {
            contextFilmStock = STANDARD_FILM_STOCK_CATALOG.get(filmStockCode);
            if (contextFilmStock == null) {
                throw ResourceNotFoundException.filmStock(filmStockCode);
            }
        }

        String title = request != null && StringUtils.hasText(request.title())
                ? request.title().trim()
                : AiChatSession.DEFAULT_TITLE_VI;

        Instant now = Instant.now();
        AiChatSession session = AiChatSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(AiChatSession.STATUS_ACTIVE)
                .title(title)
                .activeModelProvider(activeProvider)
                .contextFilmStockCode(filmStockCode)
                .createdAt(now)
                .updatedAt(now)
                .build();

        AiChatSession saved = sessionRepository.save(session);
        cacheSessionMetadata(saved, title, activeProvider, filmStockCode);

        return AiChatSessionResponse.builder()
                .sessionId(saved.getId())
                .userId(saved.getUserId())
                .title(title)
                .activeModelProvider(activeProvider.name())
                .activeModelName(aiModelRouter.resolveChatModelName(activeProvider))
                .contextFilmStock(contextFilmStock)
                .messageCount(0)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    /**
     * API 2: GET /api/v1/ai/chat/sessions
     */
    @Transactional(readOnly = true)
    public AiChatSessionListResponse listUserSessions(
            UUID userId,
            int page,
            int size,
            ModelProvider filterProvider,
            String keyword
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        String normKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;

        List<AiChatSession> allUserSessions = sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<AiChatSessionSummaryDto> filtered = new ArrayList<>();

        for (AiChatSession session : allUserSessions) {
            enrichSessionFromCache(session);
            ModelProvider provider = session.getActiveModelProvider() != null
                    ? session.getActiveModelProvider()
                    : aiProperties.resolvePrimaryProvider();
            String title = StringUtils.hasText(session.getTitle())
                    ? session.getTitle()
                    : AiChatSession.DEFAULT_TITLE_VI;

            if (filterProvider != null && provider != filterProvider) {
                continue;
            }
            if (normKeyword != null && !title.toLowerCase(Locale.ROOT).contains(normKeyword)) {
                continue;
            }

            List<AiChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
            int messageCount = messages.size();
            String lastMessagePreview = null;
            if (!messages.isEmpty()) {
                String lastContent = messages.getLast().getContent();
                if (StringUtils.hasText(lastContent)) {
                    lastMessagePreview = lastContent.length() <= MAX_PREVIEW_LENGTH
                            ? lastContent
                            : lastContent.substring(0, MAX_PREVIEW_LENGTH) + "...";
                }
            }

            filtered.add(AiChatSessionSummaryDto.builder()
                    .sessionId(session.getId())
                    .title(title)
                    .activeModelProvider(provider.name())
                    .activeModelName(aiModelRouter.resolveChatModelName(provider))
                    .lastMessagePreview(lastMessagePreview)
                    .messageCount(messageCount)
                    .createdAt(session.getCreatedAt())
                    .updatedAt(session.getUpdatedAt())
                    .build());
        }

        long totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<AiChatSessionSummaryDto> pageItems = filtered.subList(fromIndex, toIndex);
        boolean hasNext = safePage + 1 < totalPages;

        return AiChatSessionListResponse.builder()
                .items(List.copyOf(pageItems))
                .pagination(PaginationMeta.builder()
                        .page(safePage)
                        .size(safeSize)
                        .totalElements(totalElements)
                        .totalPages(totalPages)
                        .hasNext(hasNext)
                        .build())
                .build();
    }

    /**
     * API 3: GET /api/v1/ai/chat/sessions/{sessionId}/messages
     */
    @Transactional(readOnly = true)
    public AiChatHistoryResponse getSessionMessages(
            UUID userId,
            boolean isAdmin,
            UUID sessionId,
            UUID cursor,
            int limit
    ) {
        AiChatSession session = getRequiredAuthorizedSession(userId, isAdmin, sessionId);
        int safeLimit = Math.max(1, Math.min(100, limit));

        List<AiChatMessage> allMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<AiChatMessage> cursorFiltered = new ArrayList<>();

        boolean cursorReached = (cursor == null);
        for (AiChatMessage msg : allMessages) {
            enrichMessageFromCache(msg);
            if (!cursorReached) {
                if (cursor.equals(msg.getId())) {
                    cursorReached = true;
                }
                continue;
            }
            cursorFiltered.add(msg);
        }

        boolean hasMore = cursorFiltered.size() > safeLimit;
        List<AiChatMessage> slice = hasMore
                ? cursorFiltered.subList(0, safeLimit)
                : cursorFiltered;
        UUID nextCursor = hasMore && !slice.isEmpty() ? slice.getLast().getId() : null;

        List<AiChatMessageDto> dtos = slice.stream()
                .map(this::toMessageDto)
                .toList();

        ModelProvider provider = session.getActiveModelProvider() != null
                ? session.getActiveModelProvider()
                : aiProperties.resolvePrimaryProvider();

        return AiChatHistoryResponse.builder()
                .sessionId(session.getId())
                .title(StringUtils.hasText(session.getTitle()) ? session.getTitle() : AiChatSession.DEFAULT_TITLE_VI)
                .activeModelProvider(provider.name())
                .messages(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    /**
     * API 4: POST /api/v1/ai/chat/sessions/{sessionId}/messages
     * Full RAG Pipeline: InputGuardrail -> ANN Vector Retrieval -> MMR Rerank -> Abstention Gate
     * -> Grounded LLM Generation -> CitationVerifier -> OutputGuardrail -> Persist & Telemetry.
     */
    @Transactional
    public SendAiChatMessageResponse sendMessage(
            UUID userId,
            UUID sessionId,
            SendAiChatMessageRequest request
    ) {
        long startNanos = System.nanoTime();
        AiChatSession session = getRequiredAuthorizedSession(userId, false, sessionId);

        String rawQuestion = request != null ? request.question() : null;
        InputGuardrailResult inputCheck = inputGuardrail.validateAndSanitize(rawQuestion);
        String sanitizedQuestion = inputCheck.sanitizedQuestion();

        ModelProvider effectiveProvider = request != null && request.overrideProvider() != null
                ? request.overrideProvider()
                : (session.getActiveModelProvider() != null
                        ? session.getActiveModelProvider()
                        : aiProperties.resolvePrimaryProvider());
        String modelName = aiModelRouter.resolveChatModelName(effectiveProvider);

        int topK = request != null && request.topK() != null
                ? Math.max(3, Math.min(8, request.topK()))
                : RagRetrievalService.DEFAULT_RERANK_TOP_K;

        ContextFilmStockDto contextFilm = session.getContextFilmStockCode() != null
                ? STANDARD_FILM_STOCK_CATALOG.get(session.getContextFilmStockCode())
                : null;
        String filterProcessType = contextFilm != null ? contextFilm.processType() : null;
        String filterCategory = request != null ? request.filterCategory() : null;

        RetrievalResult retrievalResult = ragRetrievalService.retrieve(RetrievalQuery.builder()
                .userId(userId)
                .question(sanitizedQuestion)
                .filterProcessType(filterProcessType)
                .filterFilmStock(null)
                .filterCategory(filterCategory)
                .topK(topK)
                .similarityThreshold(RagRetrievalService.DEFAULT_ABSTENTION_THRESHOLD)
                .mmrLambda(RagRetrievalService.DEFAULT_MMR_LAMBDA)
                .build());

        if (retrievalResult.abstained() && (filterProcessType != null || filterCategory != null)) {
            retrievalResult = ragRetrievalService.retrieve(RetrievalQuery.builder()
                    .userId(userId)
                    .question(sanitizedQuestion)
                    .topK(topK)
                    .similarityThreshold(RagRetrievalService.DEFAULT_ABSTENTION_THRESHOLD)
                    .mmrLambda(RagRetrievalService.DEFAULT_MMR_LAMBDA)
                    .build());
        }

        List<AiChatMessage> existingMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Section 6.2 & Section 8: Abstention Gate (if Out-of-Domain or max similarity < 0.65)
        if (inputCheck.outOfDomain() || retrievalResult.abstained()) {
            long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
            String abstentionReason = retrievalResult.abstained() && retrievalResult.abstentionReason() != null
                    ? retrievalResult.abstentionReason()
                    : (inputCheck.outOfDomainReason() != null
                            ? inputCheck.outOfDomainReason()
                            : RagRetrievalService.ABSTENTION_REASON_LOW_SIMILARITY);
            String abstentionAnswer = StringUtils.hasText(retrievalResult.abstentionMessageVi())
                    ? retrievalResult.abstentionMessageVi()
                    : RagRetrievalService.DEFAULT_ABSTENTION_MESSAGE_VI;

            return persistTurnAndBuildResponse(
                    session,
                    existingMessages.isEmpty(),
                    sanitizedQuestion,
                    abstentionAnswer,
                    effectiveProvider,
                    modelName,
                    true,
                    abstentionReason,
                    false,
                    null,
                    0.0,
                    List.of(),
                    retrievalResult,
                    TokenUsage.of(0, 0),
                    BigDecimal.ZERO,
                    latencyMs
            );
        }

        List<ScoredChunk> contextChunks = retrievalResult.selectedChunks();
        List<LlmMessage> recentHistory = buildRecentHistory(existingMessages);
        String renderedSystemPrompt = renderSystemPrompt(contextChunks, recentHistory, sanitizedQuestion);

        LlmPromptRequest llmRequest = LlmPromptRequest.builder()
                .systemInstruction(renderedSystemPrompt)
                .history(recentHistory)
                .userPrompt(sanitizedQuestion)
                .temperature(aiProperties.getGemini().getTemperature())
                .maxOutputTokens(aiProperties.getGemini().getMaxOutputTokens())
                .responseSchemaJson(STRUCTURED_RESPONSE_SCHEMA_JSON)
                .promptKey(PROMPT_KEY_RAG_CHAT)
                .promptVersion(PROMPT_VERSION)
                .build();

        StructuredLlmResponse<RagStructuredOutput> structuredResponse = aiModelRouter.generateStructured(
                userId,
                AiModelRouter.FEATURE_RAG_CHAT,
                effectiveProvider,
                llmRequest,
                RagStructuredOutput.class
        );

        RagStructuredOutput llmData = structuredResponse != null ? structuredResponse.data() : null;
        if (llmData == null || !StringUtils.hasText(llmData.answer())) {
            llmData = synthesizeGroundedOutputFromChunks(sanitizedQuestion, contextChunks);
        }

        // Section 7.3: Citation Post-Verification
        VerificationResult verification = citationVerifier.verifyCitations(
                llmData.citations(),
                contextChunks,
                llmData.confidenceScore()
        );

        // Section 8: OutputGuardrail (System prompt leak check + Darkroom chemical safety notice)
        OutputGuardrailResult outputCheck = outputGuardrail.inspectAndEnforce(
                llmData.answer(),
                sanitizedQuestion,
                Boolean.TRUE.equals(llmData.abstained())
        );

        ModelProvider actualProvider = structuredResponse != null && structuredResponse.providerUsed() != null
                ? structuredResponse.providerUsed()
                : effectiveProvider;
        String actualModel = structuredResponse != null && StringUtils.hasText(structuredResponse.modelNameUsed())
                ? structuredResponse.modelNameUsed()
                : modelName;
        TokenUsage tokenUsage = structuredResponse != null && structuredResponse.tokenUsage() != null
                ? structuredResponse.tokenUsage()
                : TokenUsage.of(
                        Math.max(1, renderedSystemPrompt.length() / 4),
                        Math.max(1, outputCheck.finalAnswer().length() / 4)
                );
        long latencyMs = structuredResponse != null && structuredResponse.latencyMs() > 0
                ? structuredResponse.latencyMs()
                : Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        BigDecimal estimatedCostUsd = aiCostEstimator.estimateCostUsd(
                actualModel,
                tokenUsage.promptTokens(),
                tokenUsage.completionTokens()
        );

        return persistTurnAndBuildResponse(
                session,
                existingMessages.isEmpty(),
                sanitizedQuestion,
                outputCheck.finalAnswer(),
                actualProvider,
                actualModel,
                Boolean.TRUE.equals(llmData.abstained()),
                null,
                outputCheck.safetyWarningAttached(),
                outputCheck.safetyNoticeVi(),
                verification.adjustedConfidenceScore(),
                verification.verifiedCitations(),
                retrievalResult,
                tokenUsage,
                estimatedCostUsd,
                latencyMs
        );
    }

    public String renderSystemPrompt(
            List<ScoredChunk> contextChunks,
            List<LlmMessage> chatHistory,
            String userQuestion
    ) {
        StringBuilder chunksBuilder = new StringBuilder();
        if (contextChunks != null && !contextChunks.isEmpty()) {
            for (int i = 0; i < contextChunks.size(); i++) {
                ScoredChunk c = contextChunks.get(i);
                int docIdx = i + 1;
                chunksBuilder.append(String.format(
                        "[Doc-%d] (chunkId=%s, articleSlug=%s, title=%s):\n%s\n\n",
                        docIdx,
                        c.chunkId(),
                        c.articleSlug() != null ? c.articleSlug() : "unknown-slug",
                        c.chunkTitle() != null ? c.chunkTitle() : "",
                        c.chunkContent() != null ? c.chunkContent() : ""
                ));
            }
        } else {
            chunksBuilder.append("(Không có tài liệu tham khảo)\n");
        }

        StringBuilder historyBuilder = new StringBuilder();
        if (chatHistory != null && !chatHistory.isEmpty()) {
            for (LlmMessage msg : chatHistory) {
                historyBuilder.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        } else {
            historyBuilder.append("(Bắt đầu phiên hội thoại mới)\n");
        }

        return systemPromptTemplate
                .replace("{CONTEXT_CHUNKS}", chunksBuilder.toString().trim())
                .replace("{CHAT_HISTORY}", historyBuilder.toString().trim())
                .replace("{USER_QUESTION}", userQuestion != null ? userQuestion : "");
    }

    /**
     * Synthesizes a grounded response with [Doc-1] citations and verbatim quotes from the retrieved Top-K chunks
     * when running in offline/deterministic mode without external LLM API keys.
     */
    RagStructuredOutput synthesizeGroundedOutputFromChunks(String question, List<ScoredChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return RagStructuredOutput.builder()
                    .answer(RagRetrievalService.DEFAULT_ABSTENTION_MESSAGE_VI)
                    .citations(List.of())
                    .confidenceScore(0.0)
                    .abstained(true)
                    .build();
        }

        ScoredChunk primary = chunks.getFirst();
        String primaryQuote = extractRepresentativeQuote(primary.chunkContent());

        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append("Dựa trên tài liệu kỹ thuật buồng tối đã kiểm duyệt của AnalogHub (")
                .append(primary.chunkTitle() != null ? primary.chunkTitle() : primary.articleSlug())
                .append("), ")
                .append(primaryQuote)
                .append(" [Doc-1].");

        List<RawCitationItem> rawCitations = new ArrayList<>();
        rawCitations.add(RawCitationItem.builder()
                .chunkId(primary.chunkId())
                .articleSlug(primary.articleSlug())
                .quote(primaryQuote)
                .build());

        if (chunks.size() > 1) {
            ScoredChunk secondary = chunks.get(1);
            String secondaryQuote = extractRepresentativeQuote(secondary.chunkContent());
            if (StringUtils.hasText(secondaryQuote) && !secondaryQuote.equals(primaryQuote)) {
                answerBuilder.append("\n\nNgoài ra, theo hướng dẫn bổ sung: ")
                        .append(secondaryQuote)
                        .append(" [Doc-2].");
                rawCitations.add(RawCitationItem.builder()
                        .chunkId(secondary.chunkId())
                        .articleSlug(secondary.articleSlug())
                        .quote(secondaryQuote)
                        .build());
            }
        }

        return RagStructuredOutput.builder()
                .answer(answerBuilder.toString())
                .citations(rawCitations)
                .confidenceScore(0.95)
                .abstained(false)
                .build();
    }

    static String extractRepresentativeQuote(String chunkContent) {
        if (!StringUtils.hasText(chunkContent)) {
            return "";
        }
        String[] lines = chunkContent.split("\\r?\\n");
        StringBuilder bodyBuilder = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()
                    || trimmed.startsWith("[Tài liệu:")
                    || trimmed.startsWith("#")
                    || trimmed.startsWith("|---")
                    || trimmed.startsWith("| :---")) {
                continue;
            }
            if (bodyBuilder.length() > 0) {
                bodyBuilder.append(" ");
            }
            bodyBuilder.append(trimmed);
            if (bodyBuilder.length() >= 220) {
                break;
            }
        }

        String candidate = bodyBuilder.toString().trim();
        if (candidate.isEmpty()) {
            candidate = chunkContent.trim();
        }
        if (candidate.length() > 240) {
            return candidate.substring(0, 240).trim();
        }
        return candidate;
    }

    private SendAiChatMessageResponse persistTurnAndBuildResponse(
            AiChatSession session,
            boolean isFirstMessageInSession,
            String sanitizedQuestion,
            String assistantAnswer,
            ModelProvider providerUsed,
            String modelUsed,
            boolean abstained,
            String abstentionReason,
            boolean safetyWarningAttached,
            String safetyNoticeVi,
            Double confidenceScore,
            List<RagCitationDto> verifiedCitations,
            RetrievalResult retrievalResult,
            TokenUsage tokenUsage,
            BigDecimal estimatedCostUsd,
            long latencyMs
    ) {
        Instant userCreatedAt = Instant.now();
        AiChatMessage userMsg = AiChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(session.getId())
                .senderRole(AiChatMessage.ROLE_USER)
                .content(sanitizedQuestion)
                .tokensUsed(tokenUsage.promptTokens())
                .citations(List.of())
                .safetyWarningAttached(false)
                .abstained(false)
                .createdAt(userCreatedAt)
                .build();
        AiChatMessage savedUserMsg = messageRepository.save(userMsg);
        cacheMessageMetadata(savedUserMsg);

        Instant assistantCreatedAt = Instant.now();
        AiChatMessage assistantMsg = AiChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(session.getId())
                .senderRole(AiChatMessage.ROLE_ASSISTANT)
                .content(assistantAnswer)
                .tokensUsed(tokenUsage.totalTokens())
                .citations(verifiedCitations)
                .safetyWarningAttached(safetyWarningAttached)
                .abstained(abstained)
                .abstentionReason(abstentionReason)
                .promptTokens(tokenUsage.promptTokens())
                .completionTokens(tokenUsage.completionTokens())
                .latencyMs(latencyMs)
                .confidenceScore(confidenceScore)
                .promptVersion(PROMPT_VERSION)
                .createdAt(assistantCreatedAt)
                .build();
        AiChatMessage savedAssistantMsg = messageRepository.save(assistantMsg);
        cacheMessageMetadata(savedAssistantMsg);

        if (isFirstMessageInSession && AiChatSession.DEFAULT_TITLE_VI.equals(session.getTitle())) {
            String autoTitle = sanitizedQuestion.length() <= 80
                    ? sanitizedQuestion
                    : sanitizedQuestion.substring(0, 80).trim() + "...";
            session.setTitle(autoTitle);
        }
        session.setActiveModelProvider(providerUsed);
        session.setUpdatedAt(assistantCreatedAt);
        sessionRepository.save(session);
        cacheSessionMetadata(session, session.getTitle(), providerUsed, session.getContextFilmStockCode());

        return SendAiChatMessageResponse.builder()
                .sessionId(session.getId())
                .userMessageId(savedUserMsg.getId())
                .assistantMessageId(savedAssistantMsg.getId())
                .providerUsed(providerUsed.name())
                .modelUsed(modelUsed)
                .answer(assistantAnswer)
                .abstained(abstained)
                .abstentionReason(abstentionReason)
                .safetyWarningAttached(safetyWarningAttached)
                .safetyNoticeVi(safetyNoticeVi)
                .confidenceScore(confidenceScore)
                .citations(verifiedCitations)
                .retrievalTelemetry(RetrievalTelemetryDto.builder()
                        .embeddingModel(retrievalResult.embeddingModel())
                        .embeddingDimensions(retrievalResult.embeddingDimensions())
                        .initialCandidatesCount(retrievalResult.initialCandidatesCount())
                        .rerankedPassedCount(retrievalResult.rerankedPassedCount())
                        .topRerankScore(retrievalResult.topRerankScore())
                        .thresholdApplied(retrievalResult.thresholdApplied())
                        .knowledgeGraphExpandedNodes(0)
                        .build())
                .usage(UsageTelemetryDto.builder()
                        .promptTokens(tokenUsage.promptTokens())
                        .completionTokens(tokenUsage.completionTokens())
                        .totalTokens(tokenUsage.totalTokens())
                        .estimatedCostUsd(estimatedCostUsd)
                        .latencyMs(latencyMs)
                        .build())
                .createdAt(assistantCreatedAt)
                .build();
    }

    private AiChatSession getRequiredAuthorizedSession(UUID userId, boolean isAdmin, UUID sessionId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy phiên trò chuyện AI với ID: " + sessionId
                ));
        enrichSessionFromCache(session);

        if (!isAdmin && (userId == null || !userId.equals(session.getUserId()))) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bạn không có quyền truy cập phiên trò chuyện AI của người dùng khác."
            );
        }
        return session;
    }

    private List<LlmMessage> buildRecentHistory(List<AiChatMessage> existingMessages) {
        if (existingMessages == null || existingMessages.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, existingMessages.size() - MAX_HISTORY_MESSAGES);
        List<AiChatMessage> recent = existingMessages.subList(start, existingMessages.size());
        List<LlmMessage> result = new ArrayList<>(recent.size());
        for (AiChatMessage msg : recent) {
            if (AiChatMessage.ROLE_USER.equalsIgnoreCase(msg.getSenderRole())) {
                result.add(new LlmMessage("user", msg.getContent()));
            } else if (AiChatMessage.ROLE_ASSISTANT.equalsIgnoreCase(msg.getSenderRole())) {
                result.add(new LlmMessage("assistant", msg.getContent()));
            }
        }
        return result;
    }

    private AiChatMessageDto toMessageDto(AiChatMessage msg) {
        return AiChatMessageDto.builder()
                .messageId(msg.getId())
                .senderRole(msg.getSenderRole())
                .messageText(msg.getContent())
                .citations(msg.getCitations() != null ? msg.getCitations() : List.of())
                .safetyWarningAttached(msg.isSafetyWarningAttached())
                .abstained(msg.isAbstained())
                .promptTokens(msg.getPromptTokens())
                .completionTokens(msg.getCompletionTokens())
                .latencyMs(msg.getLatencyMs())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private void cacheSessionMetadata(
            AiChatSession session,
            String title,
            ModelProvider activeProvider,
            String contextFilmStockCode
    ) {
        if (session == null || session.getId() == null) {
            return;
        }
        sessionMetadataCache.put(session.getId(), new SessionMetadata(
                StringUtils.hasText(title) ? title : AiChatSession.DEFAULT_TITLE_VI,
                activeProvider != null ? activeProvider : aiProperties.resolvePrimaryProvider(),
                contextFilmStockCode
        ));
    }

    private void enrichSessionFromCache(AiChatSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        SessionMetadata cached = sessionMetadataCache.get(session.getId());
        if (cached != null) {
            if (!StringUtils.hasText(session.getTitle()) || AiChatSession.DEFAULT_TITLE_VI.equals(session.getTitle())) {
                session.setTitle(cached.title());
            }
            if (session.getActiveModelProvider() == null) {
                session.setActiveModelProvider(cached.activeModelProvider());
            }
            if (session.getContextFilmStockCode() == null) {
                session.setContextFilmStockCode(cached.contextFilmStockCode());
            }
        } else {
            if (!StringUtils.hasText(session.getTitle())) {
                session.setTitle(AiChatSession.DEFAULT_TITLE_VI);
            }
            if (session.getActiveModelProvider() == null) {
                session.setActiveModelProvider(aiProperties.resolvePrimaryProvider());
            }
        }
    }

    private void cacheMessageMetadata(AiChatMessage msg) {
        if (msg == null || msg.getId() == null) {
            return;
        }
        messageMetadataCache.put(msg.getId(), new MessageMetadata(
                msg.getCitations() != null ? List.copyOf(msg.getCitations()) : List.of(),
                msg.isSafetyWarningAttached(),
                msg.isAbstained(),
                msg.getAbstentionReason(),
                msg.getPromptTokens(),
                msg.getCompletionTokens(),
                msg.getLatencyMs(),
                msg.getConfidenceScore(),
                msg.getPromptVersion()
        ));
    }

    private void enrichMessageFromCache(AiChatMessage msg) {
        if (msg == null || msg.getId() == null) {
            return;
        }
        MessageMetadata cached = messageMetadataCache.get(msg.getId());
        if (cached != null) {
            if (msg.getCitations() == null || msg.getCitations().isEmpty()) {
                msg.setCitations(cached.citations());
            }
            msg.setSafetyWarningAttached(msg.isSafetyWarningAttached() || cached.safetyWarningAttached());
            msg.setAbstained(msg.isAbstained() || cached.abstained());
            if (msg.getAbstentionReason() == null) {
                msg.setAbstentionReason(cached.abstentionReason());
            }
            if (msg.getPromptTokens() == null) {
                msg.setPromptTokens(cached.promptTokens());
            }
            if (msg.getCompletionTokens() == null) {
                msg.setCompletionTokens(cached.completionTokens());
            }
            if (msg.getLatencyMs() == null) {
                msg.setLatencyMs(cached.latencyMs());
            }
            if (msg.getConfidenceScore() == null) {
                msg.setConfidenceScore(cached.confidenceScore());
            }
            if (msg.getPromptVersion() == null) {
                msg.setPromptVersion(cached.promptVersion());
            }
        } else if (msg.getCitations() == null) {
            msg.setCitations(List.of());
        }
    }

    private static String loadSystemPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_TEMPLATE_PATH);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ex) {
            log.warn("Could not load classpath prompt template {}, using fallback template", PROMPT_TEMPLATE_PATH);
        }
        return FALLBACK_SYSTEM_PROMPT_TEMPLATE;
    }
}
