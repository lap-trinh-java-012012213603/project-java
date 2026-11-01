-- 1. Bật extension pgvector (nếu chưa có)
CREATE EXTENSION IF NOT EXISTS vector;

-- ==========================================
-- PHẦN 1: AI ASSISTANT & KNOWLEDGE TABLES
-- ==========================================

CREATE TABLE ai_knowledge_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_text TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- Đánh HNSW Index (Sử dụng Cosine Distance)
CREATE INDEX ai_knowledge_chunks_embedding_idx ON ai_knowledge_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE ai_lab_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_id UUID NOT NULL, -- FK to labs table
    description TEXT,
    embedding vector(1536),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ai_lab_embeddings_embedding_idx ON ai_lab_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE TABLE ai_listing_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL, -- FK to listings/products table
    features TEXT,
    embedding vector(1536),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ai_listing_embeddings_embedding_idx ON ai_listing_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE TABLE ai_user_lab_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    lab_id UUID NOT NULL,
    interaction_type VARCHAR(50), -- VIEW, CHAT, ORDER, REVIEW
    interaction_score DECIMAL(3,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, CLOSED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
    sender_role VARCHAR(20) NOT NULL, -- USER, ASSISTANT, SYSTEM
    content TEXT NOT NULL,
    tokens_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_scan_qc_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL, -- FK to orders
    scan_image_url TEXT,
    qc_score DECIMAL(5,2),
    detected_issues JSONB, -- Vd: {"dust": true, "color_shift": false}
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_usage_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    action VARCHAR(100),
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_rbl_evaluation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version VARCHAR(50),
    metrics JSONB, -- Vd: {"accuracy": 0.9, "latency_ms": 120}
    run_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_rbl_evaluation_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID REFERENCES ai_rbl_evaluation_runs(id) ON DELETE CASCADE,
    prompt TEXT,
    expected_output TEXT,
    actual_output TEXT,
    is_passed BOOLEAN,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- PHẦN 2: ADMIN & MODERATION TABLES
-- ==========================================

CREATE TABLE adm_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'OPEN', -- OPEN, INVESTIGATING, RESOLVED, REJECTED
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE adm_dispute_evidences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id UUID REFERENCES adm_disputes(id) ON DELETE CASCADE,
    uploader_id UUID NOT NULL,
    file_url TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE adm_moderation_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL, -- REVIEW, LAB_PROFILE, LISTING
    entity_id UUID NOT NULL,
    reported_by UUID NOT NULL,
    issue_type VARCHAR(100),
    status VARCHAR(50) DEFAULT 'PENDING',
    assigned_admin_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE adm_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    target_entity VARCHAR(50),
    target_id UUID,
    old_values JSONB,
    new_values JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);