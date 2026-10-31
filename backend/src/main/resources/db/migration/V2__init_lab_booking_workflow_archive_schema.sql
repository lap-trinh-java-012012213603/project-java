-- ============================================================================
-- FILE: src/main/resources/db/migration/V2__init_lab_booking_workflow_archive_schema.sql
-- AnalogHub Backend v2.0 - FilmLab, Booking, LabWorkflow & DigitalArchive Schema
-- Database: PostgreSQL 16
-- Cluster B & C: filmlab (lab_*), booking (bkg_*), labworkflow (wfl_*), digitalarchive (arc_*)
-- ============================================================================

-- ============================================================================
-- TIER 2: MODULE filmlab (lab_*)
-- ============================================================================

-- 1. Film Labs (Multi-tenant Root, Soft Delete enabled)
CREATE TABLE IF NOT EXISTS lab_film_labs (
    id              UUID             PRIMARY KEY,
    owner_user_id   UUID             NOT NULL,
    slug            VARCHAR(160)     NOT NULL UNIQUE,
    name            VARCHAR(150)     NOT NULL,
    bio             TEXT             NULL,
    phone           VARCHAR(20)      NULL,
    full_address    VARCHAR(300)     NOT NULL,
    district_code   VARCHAR(32)      NOT NULL,
    province_code   VARCHAR(32)      NOT NULL,
    latitude        DOUBLE PRECISION NULL,
    longitude       DOUBLE PRECISION NULL,
    operating_hours JSONB            NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(32)      NOT NULL DEFAULT 'PENDING_APPROVAL' CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'PAUSED', 'SUSPENDED')),
    avg_rating      NUMERIC(3, 2)    NOT NULL DEFAULT 0.00 CHECK (avg_rating >= 0 AND avg_rating <= 5),
    review_count    INT              NOT NULL DEFAULT 0 CHECK (review_count >= 0),
    deleted_at      TIMESTAMPTZ      NULL,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    created_by      UUID             NULL,
    updated_by      UUID             NULL,
    version         BIGINT           NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lab_film_labs_owner
    ON lab_film_labs (owner_user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lab_film_labs_status_location
    ON lab_film_labs (status, province_code, district_code)
    WHERE deleted_at IS NULL;

-- 2. Lab Members (Intra-module FK allowed per Principle 3)
CREATE TABLE IF NOT EXISTS lab_members (
    id          UUID        PRIMARY KEY,
    lab_id      UUID        NOT NULL REFERENCES lab_film_labs (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL,
    member_role VARCHAR(32) NOT NULL CHECK (member_role IN ('OWNER', 'MANAGER', 'TECHNICIAN')),
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID        NULL,
    updated_by  UUID        NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_lab_members_lab_user UNIQUE (lab_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_lab_members_user_active
    ON lab_members (user_id, is_active);

CREATE INDEX IF NOT EXISTS idx_lab_members_lab_active
    ON lab_members (lab_id, is_active);

-- 3. Lab Service Packages (Soft Delete enabled)
CREATE TABLE IF NOT EXISTS lab_service_packages (
    id              UUID           PRIMARY KEY,
    lab_id          UUID           NOT NULL REFERENCES lab_film_labs (id) ON DELETE CASCADE,
    category_code   VARCHAR(64)    NOT NULL,
    name            VARCHAR(150)   NOT NULL,
    film_format     VARCHAR(32)    NOT NULL CHECK (film_format IN ('FMT_135', 'FMT_120', 'FMT_SHEET')),
    process_type    VARCHAR(32)    NOT NULL CHECK (process_type IN ('C41', 'ECN2', 'BW', 'E6')),
    scan_resolution VARCHAR(32)    NOT NULL DEFAULT 'NONE' CHECK (scan_resolution IN ('STD_2000', 'HIGH_4000', 'ULTRA_TIFF_6000', 'NONE')),
    unit_price_vnd  NUMERIC(15, 2) NOT NULL CHECK (unit_price_vnd >= 0),
    estimated_hours INT            NOT NULL DEFAULT 24 CHECK (estimated_hours > 0),
    is_active       BOOLEAN        NOT NULL DEFAULT true,
    deleted_at      TIMESTAMPTZ    NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      UUID           NULL,
    updated_by      UUID           NULL,
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lab_service_packages_lab_active
    ON lab_service_packages (lab_id, is_active, process_type, film_format)
    WHERE deleted_at IS NULL;

-- 4. Lab Capacity Rules (G7)
CREATE TABLE IF NOT EXISTS lab_capacity_rules (
    id                      UUID        PRIMARY KEY,
    lab_id                  UUID        NOT NULL REFERENCES lab_film_labs (id) ON DELETE CASCADE,
    process_type            VARCHAR(32) NOT NULL CHECK (process_type IN ('C41', 'ECN2', 'BW', 'E6')),
    max_daily_rolls         INT         NOT NULL CHECK (max_daily_rolls > 0),
    current_queued_rolls    INT         NOT NULL DEFAULT 0 CHECK (current_queued_rolls >= 0),
    base_turnaround_hours   INT         NOT NULL DEFAULT 24 CHECK (base_turnaround_hours > 0),
    penalty_hours_per_batch INT         NOT NULL DEFAULT 0 CHECK (penalty_hours_per_batch >= 0),
    is_accepting_orders     BOOLEAN     NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID        NULL,
    updated_by              UUID        NULL,
    version                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_lab_capacity_rules_lab_process UNIQUE (lab_id, process_type)
);

-- 5. Standardized Film Stock Catalog
CREATE TABLE IF NOT EXISTS lab_film_stock_catalog (
    id                  UUID         PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL UNIQUE,
    brand               VARCHAR(80)  NOT NULL,
    name                VARCHAR(120) NOT NULL,
    iso                 INT          NOT NULL CHECK (iso > 0),
    film_format         VARCHAR(32)  NOT NULL CHECK (film_format IN ('FMT_135', 'FMT_120', 'FMT_SHEET')),
    process_type        VARCHAR(32)  NOT NULL CHECK (process_type IN ('C41', 'ECN2', 'BW', 'E6')),
    color_balance       VARCHAR(64)  NULL,
    grain_level         VARCHAR(32)  NULL,
    characteristic_tags JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lab_film_stock_process_format
    ON lab_film_stock_catalog (process_type, film_format, iso);

-- 6. Lab Customer Reviews (1 review per order_id)
CREATE TABLE IF NOT EXISTS lab_reviews (
    id                UUID        PRIMARY KEY,
    lab_id            UUID        NOT NULL REFERENCES lab_film_labs (id) ON DELETE CASCADE,
    customer_id       UUID        NOT NULL,
    order_id          UUID        NOT NULL UNIQUE,
    rating            SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment           TEXT        NULL,
    moderation_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' CHECK (moderation_status IN ('PUBLISHED', 'HIDDEN', 'FLAGGED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID        NULL,
    updated_by        UUID        NULL,
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lab_reviews_lab_created
    ON lab_reviews (lab_id, created_at DESC)
    WHERE moderation_status = 'PUBLISHED';

-- 7. Lab Daily Operational Analytics (G7)
CREATE TABLE IF NOT EXISTS lab_daily_analytics (
    id                   UUID           PRIMARY KEY,
    lab_id               UUID           NOT NULL REFERENCES lab_film_labs (id) ON DELETE CASCADE,
    metric_date          DATE           NOT NULL,
    orders_received      INT            NOT NULL DEFAULT 0 CHECK (orders_received >= 0),
    orders_completed     INT            NOT NULL DEFAULT 0 CHECK (orders_completed >= 0),
    rolls_processed      INT            NOT NULL DEFAULT 0 CHECK (rolls_processed >= 0),
    rolls_blank_count    INT            NOT NULL DEFAULT 0 CHECK (rolls_blank_count >= 0),
    rolls_sla_breached   INT            NOT NULL DEFAULT 0 CHECK (rolls_sla_breached >= 0),
    gross_revenue_vnd    NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (gross_revenue_vnd >= 0),
    net_revenue_vnd      NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (net_revenue_vnd >= 0),
    avg_turnaround_hours NUMERIC(8, 2)  NOT NULL DEFAULT 0 CHECK (avg_turnaround_hours >= 0),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID           NULL,
    updated_by           UUID           NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_lab_daily_analytics_lab_date UNIQUE (lab_id, metric_date)
);

CREATE INDEX IF NOT EXISTS idx_lab_daily_analytics_lab_date
    ON lab_daily_analytics (lab_id, metric_date DESC);

-- ============================================================================
-- TIER 3: MODULE booking (bkg_*)
-- ============================================================================

-- 8. Lab Booking Orders
CREATE TABLE IF NOT EXISTS bkg_lab_orders (
    id                       UUID           PRIMARY KEY,
    order_code               VARCHAR(64)    NOT NULL UNIQUE,
    customer_id              UUID           NOT NULL,
    lab_id                   UUID           NOT NULL,
    status                   VARCHAR(32)    NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK (status IN ('PENDING_PAYMENT', 'PAID_WAITING_FILM', 'IN_DELIVERY_TO_LAB', 'IN_LAB_PROCESSING', 'SCAN_DELIVERED', 'RETURNING_NEGATIVE', 'COMPLETED', 'CANCELLED', 'DISPUTED', 'REFUNDED')),
    fulfillment_in           VARCHAR(32)    NOT NULL CHECK (fulfillment_in IN ('SELF_DROP_OFF', 'PICKUP_DELIVERY')),
    fulfillment_out          VARCHAR(32)    NOT NULL CHECK (fulfillment_out IN ('DIGITAL_ONLY', 'SELF_PICKUP_NEGATIVE', 'RETURN_DELIVERY_NEGATIVE')),
    negative_handling_policy VARCHAR(32)    NOT NULL DEFAULT 'HOLD_30_DAYS' CHECK (negative_handling_policy IN ('HOLD_30_DAYS', 'RETURN_IMMEDIATELY', 'DISPOSE')),
    total_rolls              INT            NOT NULL CHECK (total_rolls > 0),
    subtotal_vnd             NUMERIC(15, 2) NOT NULL CHECK (subtotal_vnd >= 0),
    delivery_fee_vnd         NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (delivery_fee_vnd >= 0),
    discount_vnd             NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (discount_vnd >= 0),
    total_amount_vnd         NUMERIC(15, 2) NOT NULL CHECK (total_amount_vnd >= 0),
    estimated_completion_at  TIMESTAMPTZ    NULL,
    payment_expires_at       TIMESTAMPTZ    NULL,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by               UUID           NULL,
    updated_by               UUID           NULL,
    version                  BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_lab_orders_customer
    ON bkg_lab_orders (customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bkg_lab_orders_lab_status
    ON bkg_lab_orders (lab_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bkg_lab_orders_unpaid_exp
    ON bkg_lab_orders (payment_expires_at)
    WHERE status = 'PENDING_PAYMENT';

-- 9. Lab Booking Order Items (Intra-module FK allowed per Principle 3)
CREATE TABLE IF NOT EXISTS bkg_lab_order_items (
    id                      UUID           PRIMARY KEY,
    order_id                UUID           NOT NULL REFERENCES bkg_lab_orders (id) ON DELETE CASCADE,
    service_package_id      UUID           NOT NULL,
    film_stock_id           UUID           NULL,
    package_name_snapshot   VARCHAR(150)   NOT NULL,
    film_format             VARCHAR(32)    NOT NULL CHECK (film_format IN ('FMT_135', 'FMT_120', 'FMT_SHEET')),
    process_type            VARCHAR(32)    NOT NULL CHECK (process_type IN ('C41', 'ECN2', 'BW', 'E6')),
    scan_resolution         VARCHAR(32)    NOT NULL DEFAULT 'NONE' CHECK (scan_resolution IN ('STD_2000', 'HIGH_4000', 'ULTRA_TIFF_6000', 'NONE')),
    roll_quantity           INT            NOT NULL CHECK (roll_quantity > 0),
    unit_price_snapshot_vnd NUMERIC(15, 2) NOT NULL CHECK (unit_price_snapshot_vnd >= 0),
    push_pull_stops         INT            NOT NULL DEFAULT 0 CHECK (push_pull_stops BETWEEN -3 AND 3),
    special_instructions    TEXT           NULL,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              UUID           NULL,
    updated_by              UUID           NULL,
    version                 BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_lab_order_items_order
    ON bkg_lab_order_items (order_id);

-- ============================================================================
-- TIER 2: MODULE labworkflow (wfl_*)
-- ============================================================================

-- 10. Physical Film Rolls in Lab Kanban Workflow
CREATE TABLE IF NOT EXISTS wfl_film_rolls (
    id                    UUID         PRIMARY KEY,
    order_id              UUID         NOT NULL,
    order_item_id         UUID         NOT NULL,
    lab_id                UUID         NOT NULL,
    customer_id           UUID         NOT NULL,
    roll_code             VARCHAR(64)  NOT NULL UNIQUE,
    qr_checkin_token_hash VARCHAR(128) NOT NULL UNIQUE,
    film_format           VARCHAR(32)  NOT NULL CHECK (film_format IN ('FMT_135', 'FMT_120', 'FMT_SHEET')),
    process_type          VARCHAR(32)  NOT NULL CHECK (process_type IN ('C41', 'ECN2', 'BW', 'E6')),
    scan_resolution       VARCHAR(32)  NOT NULL DEFAULT 'NONE' CHECK (scan_resolution IN ('STD_2000', 'HIGH_4000', 'ULTRA_TIFF_6000', 'NONE')),
    push_pull_stops       INT          NOT NULL DEFAULT 0 CHECK (push_pull_stops BETWEEN -3 AND 3),
    status                VARCHAR(32)  NOT NULL DEFAULT 'AWAITING_CHECKIN' CHECK (status IN ('AWAITING_CHECKIN', 'CHECKED_IN', 'DEVELOPING', 'WASHING_DRYING', 'SCANNING', 'BLANK_ROLL_DETECTED', 'POST_PROCESSING_QC', 'COMPLETED', 'NEGATIVE_RETURNED', 'CANCELLED')),
    is_blank_roll         BOOLEAN      NOT NULL DEFAULT false,
    checked_in_at         TIMESTAMPTZ  NULL,
    sla_deadline_at       TIMESTAMPTZ  NULL,
    sla_breach_notified   BOOLEAN      NOT NULL DEFAULT false,
    completed_at          TIMESTAMPTZ  NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID         NULL,
    updated_by            UUID         NULL,
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wfl_film_rolls_order
    ON wfl_film_rolls (order_id);

CREATE INDEX IF NOT EXISTS idx_wfl_film_rolls_lab_kanban
    ON wfl_film_rolls (lab_id, status, checked_in_at ASC);

CREATE INDEX IF NOT EXISTS idx_wfl_film_rolls_sla_monitor
    ON wfl_film_rolls (sla_deadline_at)
    WHERE sla_breach_notified = false
      AND status NOT IN ('COMPLETED', 'NEGATIVE_RETURNED', 'CANCELLED', 'BLANK_ROLL_DETECTED');

-- 11. Film Roll Status Transition Logs (Immutable Append-Only)
CREATE TABLE IF NOT EXISTS wfl_film_roll_status_logs (
    id                 UUID        PRIMARY KEY,
    film_roll_id       UUID        NOT NULL REFERENCES wfl_film_rolls (id) ON DELETE CASCADE,
    lab_id             UUID        NOT NULL,
    from_status        VARCHAR(32) NULL,
    to_status          VARCHAR(32) NOT NULL,
    changed_by_user_id UUID        NULL,
    note               TEXT        NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_wfl_roll_status_logs_roll
    ON wfl_film_roll_status_logs (film_roll_id, created_at ASC);

-- 12. Scan Upload Batches (S3 Pre-signed Batch Upload Tracking)
CREATE TABLE IF NOT EXISTS wfl_scan_upload_batches (
    id                   UUID         PRIMARY KEY,
    film_roll_id         UUID         NOT NULL REFERENCES wfl_film_rolls (id) ON DELETE CASCADE,
    lab_id               UUID         NOT NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'PRESIGNED_ISSUED' CHECK (status IN ('PRESIGNED_ISSUED', 'UPLOADING', 'COMPLETED', 'FAILED')),
    expected_frame_count INT          NOT NULL CHECK (expected_frame_count > 0),
    uploaded_frame_count INT          NOT NULL DEFAULT 0 CHECK (uploaded_frame_count >= 0),
    s3_prefix            VARCHAR(512) NOT NULL,
    confirmed_at         TIMESTAMPTZ  NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID         NULL,
    updated_by           UUID         NULL,
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wfl_scan_batches_roll
    ON wfl_scan_upload_batches (film_roll_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wfl_scan_batches_lab_status
    ON wfl_scan_upload_batches (lab_id, status);

-- ============================================================================
-- TIER 2: MODULE digitalarchive (arc_*)
-- ============================================================================

-- 13. Digital Archive Albums (Soft Delete enabled)
CREATE TABLE IF NOT EXISTS arc_albums (
    id            UUID         PRIMARY KEY,
    owner_user_id UUID         NOT NULL,
    title         VARCHAR(150) NOT NULL,
    description   TEXT         NULL,
    visibility    VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'UNLISTED_LINK', 'PUBLIC_COMMUNITY')),
    deleted_at    TIMESTAMPTZ  NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID         NULL,
    updated_by    UUID         NULL,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_arc_albums_owner
    ON arc_albums (owner_user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- 14. Digital Archive Rolls (Soft Delete enabled)
CREATE TABLE IF NOT EXISTS arc_rolls (
    id                   UUID         PRIMARY KEY,
    owner_user_id        UUID         NOT NULL,
    album_id             UUID         NULL REFERENCES arc_albums (id) ON DELETE SET NULL,
    source_film_roll_id  UUID         NOT NULL UNIQUE,
    source_order_id      UUID         NOT NULL,
    source_lab_id        UUID         NOT NULL,
    film_stock_id        UUID         NULL,
    roll_title           VARCHAR(150) NOT NULL,
    camera_model         VARCHAR(120) NULL,
    lens_model           VARCHAR(120) NULL,
    shot_iso             INT          NULL CHECK (shot_iso IS NULL OR shot_iso > 0),
    shot_date            DATE         NULL,
    process_type         VARCHAR(32)  NULL CHECK (process_type IS NULL OR process_type IN ('C41', 'ECN2', 'BW', 'E6')),
    scan_scanner_model   VARCHAR(80)  NULL,
    contact_sheet_s3_key VARCHAR(512) NULL,
    frame_count          INT          NOT NULL DEFAULT 0 CHECK (frame_count >= 0),
    total_size_bytes     BIGINT       NOT NULL DEFAULT 0 CHECK (total_size_bytes >= 0),
    raw_expires_at       TIMESTAMPTZ  NULL,
    deleted_at           TIMESTAMPTZ  NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID         NULL,
    updated_by           UUID         NULL,
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_arc_rolls_owner
    ON arc_rolls (owner_user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_arc_rolls_album
    ON arc_rolls (album_id)
    WHERE deleted_at IS NULL AND album_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_arc_rolls_raw_expires
    ON arc_rolls (raw_expires_at)
    WHERE raw_expires_at IS NOT NULL AND deleted_at IS NULL;

-- 15. Digital Archive Scanned Frames
CREATE TABLE IF NOT EXISTS arc_frames (
    id               UUID          PRIMARY KEY,
    archive_roll_id  UUID          NOT NULL REFERENCES arc_rolls (id) ON DELETE CASCADE,
    owner_user_id    UUID          NOT NULL,
    frame_number     SMALLINT      NOT NULL CHECK (frame_number > 0),
    s3_key_original  VARCHAR(512)  NOT NULL,
    s3_key_web       VARCHAR(512)  NOT NULL,
    s3_key_thumb     VARCHAR(512)  NOT NULL,
    width_px         INT           NULL CHECK (width_px IS NULL OR width_px > 0),
    height_px        INT           NULL CHECK (height_px IS NULL OR height_px > 0),
    size_bytes       BIGINT        NOT NULL DEFAULT 0 CHECK (size_bytes >= 0),
    sha256_checksum  CHAR(64)      NULL,
    qc_status        VARCHAR(32)   NOT NULL DEFAULT 'PENDING_AI' CHECK (qc_status IN ('PENDING_AI', 'GOOD', 'UNDEREXPOSED', 'OVEREXPOSED', 'BLURRY', 'LIGHT_LEAK', 'DUST_SCRATCH')),
    qc_overall_score NUMERIC(5, 2) NULL CHECK (qc_overall_score IS NULL OR (qc_overall_score >= 0 AND qc_overall_score <= 100)),
    exif_metadata    JSONB         NOT NULL DEFAULT '{}'::jsonb,
    is_favorite      BOOLEAN       NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID          NULL,
    updated_by       UUID          NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_arc_frames_roll_frame UNIQUE (archive_roll_id, frame_number)
);

CREATE INDEX IF NOT EXISTS idx_arc_frames_roll_order
    ON arc_frames (archive_roll_id, frame_number ASC);

CREATE INDEX IF NOT EXISTS idx_arc_frames_owner_favorite
    ON arc_frames (owner_user_id, is_favorite)
    WHERE is_favorite = true;
