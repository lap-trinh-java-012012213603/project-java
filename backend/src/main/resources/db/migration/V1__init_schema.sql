-- ============================================================================
-- FILE: src/main/resources/db/migration/V1__init_schema.sql
-- AnalogHub Backend v2.0 - Core Infrastructure, PlatformConfig & IAM Schema
-- Database: PostgreSQL 16 + pgvector
-- Cluster A: common (Outbox, ShedLock, Idempotency), platformconfig, iam
-- ============================================================================

-- 1. Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- ============================================================================
-- TIER 0: SHARED INFRASTRUCTURE KERNEL (Outbox, ShedLock, Idempotency)
-- ============================================================================

-- 2. Spring Modulith Transactional Outbox Table (ADR-02, G9)
CREATE TABLE IF NOT EXISTS event_publication (
    id                     UUID         PRIMARY KEY,
    listener_id            VARCHAR(512) NOT NULL,
    event_type             VARCHAR(512) NOT NULL,
    serialized_event       TEXT         NOT NULL,
    publication_date       TIMESTAMPTZ  NOT NULL,
    completion_date        TIMESTAMPTZ  NULL,
    status                 VARCHAR(32)  NULL,
    completion_attempts    INT          NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS idx_evt_pub_incomplete
    ON event_publication (publication_date ASC)
    WHERE completion_date IS NULL;

CREATE INDEX IF NOT EXISTS idx_evt_pub_listener_status
    ON event_publication (listener_id, status);

-- 3. ShedLock Table for Distributed Scheduler Locking (G9)
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- 4. Durable Idempotency Store (G9 & Principle 5)
CREATE TABLE IF NOT EXISTS sys_idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    user_id         UUID         NULL,
    request_path    VARCHAR(255) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(32)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    response_status INT          NULL,
    response_body   JSONB        NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sys_idemp_expires
    ON sys_idempotency_keys (expires_at);

-- ============================================================================
-- TIER 1: MODULE platformconfig (cfg_*)
-- ============================================================================

-- 5. System Configurations
CREATE TABLE IF NOT EXISTS cfg_system_configurations (
    config_key   VARCHAR(128) PRIMARY KEY,
    config_value TEXT         NOT NULL,
    value_type   VARCHAR(32)  NOT NULL DEFAULT 'STRING' CHECK (value_type IN ('STRING', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN', 'JSON')),
    description  TEXT         NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID         NULL,
    updated_by   UUID         NULL,
    version      BIGINT       NOT NULL DEFAULT 0
);

-- 6. Platform Fee & Commission Configurations (G6)
CREATE TABLE IF NOT EXISTS cfg_fee_configurations (
    id              UUID           PRIMARY KEY,
    fee_scope       VARCHAR(64)    NOT NULL UNIQUE CHECK (fee_scope IN ('LAB_ORDER', 'MARKETPLACE_SALE', 'MARKETPLACE_SWAP', 'WORKSHOP_TICKET')),
    commission_rate NUMERIC(5, 4)  NOT NULL CHECK (commission_rate >= 0 AND commission_rate <= 1),
    fixed_fee_vnd   NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (fixed_fee_vnd >= 0),
    min_fee_vnd     NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (min_fee_vnd >= 0),
    max_fee_vnd     NUMERIC(15, 2) NULL CHECK (max_fee_vnd IS NULL OR max_fee_vnd >= min_fee_vnd),
    is_active       BOOLEAN        NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      UUID           NULL,
    updated_by      UUID           NULL,
    version         BIGINT         NOT NULL DEFAULT 0
);

-- 7. Standardized Service Categories
CREATE TABLE IF NOT EXISTS cfg_service_categories (
    id             UUID         PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL UNIQUE,
    name           VARCHAR(150) NOT NULL,
    category_group VARCHAR(32)  NOT NULL CHECK (category_group IN ('DEVELOPING', 'SCANNING', 'PRINTING')),
    description    TEXT         NULL,
    display_order  INT          NOT NULL DEFAULT 0,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NULL,
    updated_by     UUID         NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_cfg_service_categories_group
    ON cfg_service_categories (category_group, is_active);

-- ============================================================================
-- TIER 1: MODULE iam (iam_*)
-- ============================================================================

-- 8. Users (Soft Delete enabled)
CREATE TABLE IF NOT EXISTS iam_users (
    id                UUID         PRIMARY KEY,
    email             VARCHAR(150) NOT NULL UNIQUE,
    phone_number      VARCHAR(20)  NULL UNIQUE,
    password_hash     VARCHAR(255) NULL,
    full_name         VARCHAR(120) NOT NULL,
    avatar_key        VARCHAR(255) NULL,
    account_status    VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    auth_provider     VARCHAR(32)  NOT NULL DEFAULT 'LOCAL' CHECK (auth_provider IN ('LOCAL', 'GOOGLE')),
    provider_id       VARCHAR(150) NULL,
    style_preferences JSONB        NOT NULL DEFAULT '{}'::jsonb,
    deleted_at        TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID         NULL,
    updated_by        UUID         NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_iam_users_status
    ON iam_users (account_status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_iam_users_provider
    ON iam_users (auth_provider, provider_id)
    WHERE provider_id IS NOT NULL;

-- 9. Roles
CREATE TABLE IF NOT EXISTS iam_roles (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE CHECK (code IN ('CUSTOMER', 'LAB_OWNER', 'EXPERT', 'DELIVERY_PARTNER', 'ADMIN')),
    name        VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID         NULL,
    updated_by  UUID         NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- 10. User Role Assignments (Intra-module FK allowed per Principle 3)
CREATE TABLE IF NOT EXISTS iam_user_roles (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES iam_users (id) ON DELETE CASCADE,
    role_id    UUID        NOT NULL REFERENCES iam_roles (id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID        NULL,
    updated_by UUID        NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_user_roles_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_iam_user_roles_user_id
    ON iam_user_roles (user_id);

CREATE INDEX IF NOT EXISTS idx_iam_user_roles_role_id
    ON iam_user_roles (role_id);

-- 11. User Addresses (Soft Delete enabled)
CREATE TABLE IF NOT EXISTS iam_user_addresses (
    id              UUID             PRIMARY KEY,
    user_id         UUID             NOT NULL REFERENCES iam_users (id) ON DELETE CASCADE,
    recipient_name  VARCHAR(120)     NOT NULL,
    recipient_phone VARCHAR(20)      NOT NULL,
    full_address    VARCHAR(300)     NOT NULL,
    ward_code       VARCHAR(32)      NULL,
    district_code   VARCHAR(32)      NOT NULL,
    province_code   VARCHAR(32)      NOT NULL,
    latitude        DOUBLE PRECISION NULL,
    longitude       DOUBLE PRECISION NULL,
    is_default      BOOLEAN          NOT NULL DEFAULT false,
    deleted_at      TIMESTAMPTZ      NULL,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    created_by      UUID             NULL,
    updated_by      UUID             NULL,
    version         BIGINT           NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_iam_user_addresses_user_active
    ON iam_user_addresses (user_id, is_default DESC)
    WHERE deleted_at IS NULL;

-- 12. Refresh Tokens (Family Rotation & Reuse Detection)
CREATE TABLE IF NOT EXISTS iam_refresh_tokens (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES iam_users (id) ON DELETE CASCADE,
    token_family_id UUID         NOT NULL,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    device_info     VARCHAR(255) NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         NULL,
    updated_by      UUID         NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_iam_refresh_tokens_user_id
    ON iam_refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_iam_refresh_tokens_family_id
    ON iam_refresh_tokens (token_family_id);

CREATE INDEX IF NOT EXISTS idx_iam_refresh_tokens_expires_at
    ON iam_refresh_tokens (expires_at);

-- 13. Role Upgrade Applications (G5: LAB_OWNER, EXPERT, DELIVERY_PARTNER)
CREATE TABLE IF NOT EXISTS iam_role_applications (
    id                   UUID        PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES iam_users (id) ON DELETE CASCADE,
    requested_role       VARCHAR(32) NOT NULL CHECK (requested_role IN ('LAB_OWNER', 'EXPERT', 'DELIVERY_PARTNER')),
    portfolio_payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    status               VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by_admin_id UUID        NULL,
    review_note          TEXT        NULL,
    reviewed_at          TIMESTAMPTZ NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID        NULL,
    updated_by           UUID        NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_iam_role_apps_user_status
    ON iam_role_applications (user_id, status);

CREATE INDEX IF NOT EXISTS idx_iam_role_apps_status_created
    ON iam_role_applications (status, created_at ASC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_role_apps_pending
    ON iam_role_applications (user_id, requested_role)
    WHERE status = 'PENDING';
