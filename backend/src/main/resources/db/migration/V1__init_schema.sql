-- =============================================================================
-- FILE: src/main/resources/db/migration/V1__init_schema.sql
-- PROJECT: AnalogHub - Film Photography & MiniLab AI Platform
-- DATABASE: PostgreSQL 16 + pgvector (768 dimensions for Gemini text-embedding-004)
-- ID STRATEGY: VARCHAR(36) storing UUID String
-- =============================================================================

-- 1. ENABLE EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- MODULE 1: IAM & USERS
-- =============================================================================
CREATE TABLE users
(
    id                       VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email                    VARCHAR(150) NOT NULL UNIQUE,
    password_hash            VARCHAR(255),
    full_name                VARCHAR(120) NOT NULL,
    phone                    VARCHAR(20) UNIQUE,
    avatar_key               VARCHAR(255),
    role                     VARCHAR(30)  NOT NULL   DEFAULT 'CUSTOMER',  -- CUSTOMER, LAB_OWNER, ADMIN
    auth_provider            VARCHAR(30)  NOT NULL   DEFAULT 'LOCAL',     -- LOCAL, GOOGLE
    provider_id              VARCHAR(150),
    is_expert                BOOLEAN      NOT NULL   DEFAULT FALSE,
    is_active                BOOLEAN      NOT NULL   DEFAULT TRUE,
    has_completed_onboarding BOOLEAN      NOT NULL   DEFAULT FALSE,
    style_preferences        JSONB                   DEFAULT '{}'::jsonb, -- Cold-start preferences (formats, processes, tone, location)
    preference_embedding     vector(768),                                 -- Vector sở thích người dùng cho Content-based RecSys
    created_at               TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100)
);

CREATE INDEX idx_users_role ON users(role);

-- =============================================================================
-- MODULE 2: FILM LABS & FILM STOCK CATALOG
-- =============================================================================
CREATE TABLE labs
(
    id                   VARCHAR(36) PRIMARY KEY   DEFAULT gen_random_uuid()::text,
    owner_id             VARCHAR(36)      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name                 VARCHAR(150)     NOT NULL,
    slug                 VARCHAR(160)     NOT NULL UNIQUE,
    description          TEXT,
    address              VARCHAR(300)     NOT NULL,
    city                 VARCHAR(80)      NOT NULL,
    district             VARCHAR(80)      NOT NULL,
    latitude             DOUBLE PRECISION NOT NULL,
    longitude            DOUBLE PRECISION NOT NULL,
    phone                VARCHAR(20),
    cover_image_key      VARCHAR(255),
    gallery_keys         TEXT[] DEFAULT '{}',
    supported_formats    TEXT[] NOT NULL DEFAULT '{135,120}',     -- 135, 120, LARGE_FORMAT
    supported_processes  TEXT[] NOT NULL DEFAULT '{C41,ECN2,BW}', -- C41, ECN2, BW, E6
    scanners             TEXT[] NOT NULL DEFAULT '{NORITSU,FRONTIER}',
    avg_turnaround_hours INT              NOT NULL DEFAULT 24,
    starting_price       NUMERIC(12, 0)   NOT NULL DEFAULT 60000,
    avg_rating           REAL             NOT NULL DEFAULT 5.0,
    review_count         INT              NOT NULL DEFAULT 0,
    is_open              BOOLEAN          NOT NULL DEFAULT TRUE,
    es_synced            BOOLEAN          NOT NULL DEFAULT FALSE, -- Cờ Outbox đồng bộ sang Elasticsearch
    feature_embedding    vector(768),                             -- Vector đặc trưng Lab cho AI Recommendation
    created_at           TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100)
);

CREATE INDEX idx_labs_owner_id ON labs(owner_id);
CREATE INDEX idx_labs_city_district ON labs(city, district);
CREATE INDEX idx_labs_es_synced ON labs(es_synced) WHERE es_synced = FALSE;
CREATE INDEX idx_labs_embedding_hnsw ON labs USING hnsw (feature_embedding vector_cosine_ops);

CREATE TABLE lab_service_items
(
    id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    lab_id           VARCHAR(36)    NOT NULL REFERENCES labs (id) ON DELETE CASCADE, -- Tenant ID
    name             VARCHAR(150)   NOT NULL,
    service_type     VARCHAR(30)    NOT NULL,                                        -- DEV_ONLY, DEV_SCAN, SCAN_ONLY, PRINT_ADDON
    format           VARCHAR(20)    NOT NULL,                                        -- 135, 120
    process_type     VARCHAR(20)    NOT NULL,                                        -- C41, ECN2, BW, E6
    scanner_model    VARCHAR(50),                                                    -- NORITSU, FRONTIER, DSLR
    resolution       VARCHAR(30)    NOT NULL,                                        -- STANDARD_JPEG, HIGH_JPEG, TIFF_16BIT
    price            NUMERIC(12, 0) NOT NULL,
    turnaround_hours INT            NOT NULL DEFAULT 24,
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100)
);

CREATE INDEX idx_lab_services_tenant ON lab_service_items(lab_id, is_active);

CREATE TABLE film_stocks
(
    id                 VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    brand              VARCHAR(80)  NOT NULL, -- Kodak, Fujifilm, Ilford, Cinestill...
    name               VARCHAR(120) NOT NULL, -- Portra 400, Gold 200, Vision3 500T...
    slug               VARCHAR(140) NOT NULL UNIQUE,
    iso                INT          NOT NULL,
    format             VARCHAR(20)  NOT NULL   DEFAULT '135',
    process_type       VARCHAR(20)  NOT NULL   DEFAULT 'C41',
    color_profile_desc TEXT         NOT NULL, -- Mô tả tông màu, độ hạt, độ tương phản
    box_image_key      VARCHAR(255),
    sample_tags        TEXT[] DEFAULT '{}',
    embedding          vector(768),           -- Vector tìm kiếm ngữ nghĩa loại phim
    created_at         TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100)
);

CREATE INDEX idx_film_stocks_process ON film_stocks(process_type, iso);
CREATE INDEX idx_film_stocks_embedding_hnsw ON film_stocks USING hnsw (embedding vector_cosine_ops);

-- =============================================================================
-- MODULE 3: ORDERS, FILM ROLL WORKFLOW (STATE MACHINE) & LAB REVIEWS
-- =============================================================================
CREATE TABLE orders
(
    id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    order_code       VARCHAR(30)    NOT NULL UNIQUE,
    customer_id      VARCHAR(36)    NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    lab_id           VARCHAR(36)    NOT NULL REFERENCES labs (id) ON DELETE RESTRICT, -- Tenant ID
    fulfillment_type VARCHAR(30)    NOT NULL,                                         -- SELF_DROP, PICKUP_DELIVERY
    pickup_address   VARCHAR(300),
    customer_phone   VARCHAR(20)    NOT NULL,
    customer_note    TEXT,
    subtotal_amount  NUMERIC(12, 0) NOT NULL,
    shipping_fee     NUMERIC(12, 0) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(12, 0) NOT NULL,
    payment_method   VARCHAR(30)    NOT NULL,                                         -- VNPAY, COD
    payment_status   VARCHAR(30)    NOT NULL DEFAULT 'UNPAID',                        -- UNPAID, PAID, REFUNDED, FAILED
    vnpay_txn_ref    VARCHAR(100),
    delivery_status  VARCHAR(40)    NOT NULL DEFAULT 'PENDING',                       -- PENDING, PICKING_UP, DELIVERED_TO_LAB, RETURNED
    tracking_code    VARCHAR(80),
    status           VARCHAR(40)    NOT NULL DEFAULT 'PENDING',                       -- PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100)
);

CREATE INDEX idx_orders_customer ON orders(customer_id, created_at DESC);
CREATE INDEX idx_orders_lab_tenant ON orders(lab_id, status, created_at DESC);

CREATE TABLE order_rolls
(
    id                  VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    order_id            VARCHAR(36)    NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    lab_id              VARCHAR(36)    NOT NULL REFERENCES labs (id) ON DELETE RESTRICT, -- Denormalized Tenant ID
    customer_id         VARCHAR(36)    NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    film_stock_id       VARCHAR(36)    REFERENCES film_stocks (id) ON DELETE SET NULL,
    lab_service_item_id VARCHAR(36)    REFERENCES lab_service_items (id) ON DELETE SET NULL,
    roll_code           VARCHAR(40)    NOT NULL,                                         -- Mã dán lên vỏ cuộn phim (VD: R-8821-01)
    format              VARCHAR(20)    NOT NULL,                                         -- 135, 120
    process_type        VARCHAR(20)    NOT NULL,                                         -- C41, ECN2, BW
    scanner_preference  VARCHAR(50),                                                     -- NORITSU, FRONTIER
    resolution          VARCHAR(30)    NOT NULL,
    push_pull_stops     INT            NOT NULL DEFAULT 0,                               -- -1, 0, +1, +2
    price               NUMERIC(12, 0) NOT NULL,
    status              VARCHAR(40)    NOT NULL DEFAULT 'AWAITING_DROP_OFF',             -- State Machine
    camera_used         VARCHAR(120),
    lens_used           VARCHAR(120),
    shot_date           DATE,
    location_tag        VARCHAR(150),
    archive_unlocked    BOOLEAN        NOT NULL DEFAULT FALSE,
    qc_passed_count     INT            NOT NULL DEFAULT 0,
    qc_warning_count    INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    UNIQUE (lab_id, roll_code)
);

CREATE INDEX idx_order_rolls_order ON order_rolls(order_id);
CREATE INDEX idx_order_rolls_lab_kanban ON order_rolls(lab_id, status);
CREATE INDEX idx_order_rolls_customer_archive ON order_rolls(customer_id, archive_unlocked);

CREATE TABLE roll_status_logs
(
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    order_roll_id   VARCHAR(36) NOT NULL REFERENCES order_rolls (id) ON DELETE CASCADE,
    lab_id          VARCHAR(36) NOT NULL,
    previous_status VARCHAR(40),
    new_status      VARCHAR(40) NOT NULL,
    operator_note   TEXT,
    changed_by      VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL    DEFAULT NOW()
);

CREATE INDEX idx_roll_status_logs_roll ON roll_status_logs(order_roll_id, created_at ASC);

CREATE TABLE lab_reviews
(
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    order_id    VARCHAR(36) NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    lab_id      VARCHAR(36) NOT NULL REFERENCES labs (id) ON DELETE CASCADE,
    customer_id VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating      INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL    DEFAULT NOW()
);

CREATE INDEX idx_lab_reviews_lab ON lab_reviews(lab_id, created_at DESC);

-- =============================================================================
-- MODULE 4: DIGITAL FILM ARCHIVE & SCAN QUALITY CONTROL (QC)
-- =============================================================================
CREATE TABLE scan_photos
(
    id                 VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    order_roll_id      VARCHAR(36)  NOT NULL REFERENCES order_rolls (id) ON DELETE CASCADE,
    lab_id             VARCHAR(36)  NOT NULL REFERENCES labs (id) ON DELETE RESTRICT, -- Tenant ID
    customer_id        VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    frame_number       INT          NOT NULL CHECK (frame_number BETWEEN 1 AND 45),
    original_key       VARCHAR(300) NOT NULL,                                         -- private/tenants/{labId}/rolls/{rollId}/frames/01_original.jpg
    preview_key        VARCHAR(300) NOT NULL,                                         -- private/tenants/{labId}/rolls/{rollId}/frames/01_preview.webp
    thumb_key          VARCHAR(300) NOT NULL,                                         -- private/tenants/{labId}/rolls/{rollId}/frames/01_thumb.webp
    file_size_bytes    BIGINT       NOT NULL,
    content_type       VARCHAR(50)  NOT NULL,
    sharpness_score    REAL,                                                          -- Tầng 1 CV: Phương sai Laplacian
    exposure_mean      REAL,                                                          -- Tầng 1 CV: Trung bình độ sáng Luminance (0..255)
    dark_pixel_ratio   REAL,                                                          -- Tầng 1 CV: Tỷ lệ điểm ảnh bết tối (< 10)
    bright_pixel_ratio REAL,                                                          -- Tầng 1 CV: Tỷ lệ điểm ảnh cháy sáng (> 245)
    cv_flags           TEXT[] DEFAULT '{}',                                           -- BLURRY, UNDEREXPOSED, OVEREXPOSED_OR_LIGHT_LEAK
    qc_status          VARCHAR(30)  NOT NULL   DEFAULT 'PENDING',                     -- PENDING, PASSED, WARNING, FAILED
    ai_critique        JSONB,                                                         -- Tầng 2 Gemini Vision JSON output
    is_favorite        BOOLEAN      NOT NULL   DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    UNIQUE (order_roll_id, frame_number)
);

CREATE INDEX idx_scan_photos_roll ON scan_photos(order_roll_id, frame_number ASC);
CREATE INDEX idx_scan_photos_tenant ON scan_photos(lab_id, qc_status);

-- =============================================================================
-- MODULE 5: MARKETPLACE (CAMERA, LENS, FILM, ACCESSORY)
-- =============================================================================
CREATE TABLE market_listings
(
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    seller_id   VARCHAR(36)    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(200)   NOT NULL,
    slug        VARCHAR(220)   NOT NULL UNIQUE,
    description TEXT           NOT NULL,
    category    VARCHAR(30)    NOT NULL,                     -- CAMERA, LENS, FILM, ACCESSORY
    brand       VARCHAR(80),
    model_name  VARCHAR(120),
    condition   VARCHAR(30)    NOT NULL,                     -- MINT, NEAR_MINT, GOOD, CLA_NEEDED
    price       NUMERIC(12, 0) NOT NULL,
    city        VARCHAR(80)    NOT NULL,
    image_keys  TEXT[] NOT NULL DEFAULT '{}',
    status      VARCHAR(30)    NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, RESERVED, SOLD, HIDDEN
    es_synced   BOOLEAN        NOT NULL DEFAULT FALSE,
    embedding   vector(768),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

CREATE INDEX idx_market_listings_seller ON market_listings(seller_id);
CREATE INDEX idx_market_listings_filter ON market_listings(category, status, price);
CREATE INDEX idx_market_listings_es_synced ON market_listings(es_synced) WHERE es_synced = FALSE;
CREATE INDEX idx_market_listings_embedding_hnsw ON market_listings USING hnsw (embedding vector_cosine_ops);

CREATE TABLE listing_offers
(
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    listing_id    VARCHAR(36)    NOT NULL REFERENCES market_listings (id) ON DELETE CASCADE,
    buyer_id      VARCHAR(36)    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    offered_price NUMERIC(12, 0) NOT NULL,
    buyer_phone   VARCHAR(20)    NOT NULL,
    message       TEXT,
    status        VARCHAR(30)    NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED
    seller_reply  TEXT,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_listing_offers_listing ON listing_offers(listing_id, created_at DESC);

CREATE TABLE seller_reviews
(
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    listing_id  VARCHAR(36) NOT NULL UNIQUE REFERENCES market_listings (id) ON DELETE CASCADE,
    seller_id   VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reviewer_id VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating      INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL    DEFAULT NOW()
);

-- =============================================================================
-- MODULE 6: COMMUNITY & KNOWLEDGE SHARING
-- =============================================================================
CREATE TABLE community_posts
(
    id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    author_id        VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    film_stock_id    VARCHAR(36)  REFERENCES film_stocks (id) ON DELETE SET NULL,
    title            VARCHAR(220) NOT NULL,
    slug             VARCHAR(240) NOT NULL UNIQUE,
    post_type        VARCHAR(30)  NOT NULL   DEFAULT 'GUIDE', -- GUIDE, REVIEW, WORKSHOP, PHOTOWALK
    content_markdown TEXT         NOT NULL,
    cover_image_key  VARCHAR(255),
    tags             TEXT[] DEFAULT '{}',
    event_date       TIMESTAMPTZ,
    event_location   VARCHAR(255),
    max_participants INT,
    view_count       INT          NOT NULL   DEFAULT 0,
    like_count       INT          NOT NULL   DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL   DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100)
);

CREATE INDEX idx_community_posts_type ON community_posts(post_type, created_at DESC);
CREATE INDEX idx_community_posts_film_stock ON community_posts(film_stock_id);

CREATE TABLE post_comments (
                               id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                               post_id           VARCHAR(36) NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
                               author_id         VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               parent_comment_id VARCHAR(36) REFERENCES post_comments(id) ON DELETE CASCADE,
                               content           TEXT NOT NULL,
                               created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_comments_post ON post_comments(post_id, created_at ASC);

CREATE TABLE event_registrations
(
    id         VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    post_id    VARCHAR(36) NOT NULL REFERENCES community_posts (id) ON DELETE CASCADE,
    user_id    VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    phone      VARCHAR(20) NOT NULL,
    note       VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL    DEFAULT NOW(),
    UNIQUE (post_id, user_id)
);

-- =============================================================================
-- MODULE 7: AI RAG KNOWLEDGE CHUNKS & RBL INTERACTION LOGS
-- =============================================================================
CREATE TABLE knowledge_chunks
(
    id             VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    source_post_id VARCHAR(36) REFERENCES community_posts (id) ON DELETE CASCADE,
    source_title   VARCHAR(250) NOT NULL,
    category       VARCHAR(50)  NOT NULL, -- FILM_STOCK, TROUBLESHOOTING, LAB_GUIDE, DEV_PROCESS
    content        TEXT         NOT NULL,
    content_tsv    TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', coalesce(source_title, '') || ' ' ||
                                                                       coalesce(content, ''))) STORED,
    embedding      vector(768) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL   DEFAULT NOW()
);

-- Hỗ trợ Hybrid Retrieval (Full-text GIN + Vector HNSW) cho RAG Chatbot
CREATE INDEX idx_knowledge_chunks_tsv ON knowledge_chunks USING gin (content_tsv);
CREATE INDEX idx_knowledge_chunks_embedding_hnsw ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);

-- Bảng lưu ma trận tương tác User - Lab phục vụ Collaborative Filtering (SVD / KNN)
CREATE TABLE user_lab_interactions
(
    id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id          VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    lab_id           VARCHAR(36) NOT NULL REFERENCES labs (id) ON DELETE CASCADE,
    interaction_type VARCHAR(30) NOT NULL, -- VIEW, BOOKMARK, ORDER, REVIEW
    weight           REAL        NOT NULL, -- VIEW=1.0, BOOKMARK=2.0, ORDER=3.5, REVIEW_HIGH=5.0
    rating_score     REAL,
    created_at       TIMESTAMPTZ NOT NULL    DEFAULT NOW()
);

CREATE INDEX idx_interactions_user_lab ON user_lab_interactions(user_id, lab_id);