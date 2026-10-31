-- Payment tables

CREATE TABLE IF NOT EXISTS pay_wallets (
    id                    UUID           PRIMARY KEY,
    owner_user_id         UUID           UNIQUE,
    wallet_type           VARCHAR(32)    NOT NULL CHECK (wallet_type IN ('USER_WALLET', 'LAB_WALLET', 'SYSTEM_ESCROW', 'SYSTEM_REVENUE')),
    available_balance_vnd NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (available_balance_vnd >= 0),
    locked_balance_vnd    NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (locked_balance_vnd >= 0),
    currency              VARCHAR(8)     NOT NULL DEFAULT 'VND',
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID           NULL,
    updated_by            UUID           NULL,
    version               BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_transactions (
    id               UUID           PRIMARY KEY,
    txn_code         VARCHAR(64)    NOT NULL UNIQUE,
    payer_user_id    UUID           NOT NULL,
    payment_purpose  VARCHAR(32)    NOT NULL CHECK (payment_purpose IN ('LAB_ORDER', 'MARKETPLACE_ORDER', 'MARKETPLACE_SWAP_DEPOSIT', 'WORKSHOP_TICKET')),
    reference_id     UUID           NOT NULL,
    gateway_provider VARCHAR(32)    NOT NULL CHECK (gateway_provider IN ('VNPAY', 'INTERNAL_WALLET')),
    gateway_txn_no   VARCHAR(128)   NULL,
    amount_vnd       NUMERIC(15, 2) NOT NULL CHECK (amount_vnd > 0),
    status           VARCHAR(32)    NOT NULL CHECK (status IN ('INITIATED', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    expires_at       TIMESTAMPTZ    NOT NULL,
    paid_at          TIMESTAMPTZ    NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       UUID           NULL,
    updated_by       UUID           NULL,
    version          BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pay_txn_ref ON pay_transactions (payment_purpose, reference_id);
CREATE INDEX IF NOT EXISTS idx_pay_txn_exp ON pay_transactions (expires_at) WHERE status = 'INITIATED';

CREATE TABLE IF NOT EXISTS pay_escrow_holds (
    id                    UUID           PRIMARY KEY,
    payment_txn_id        UUID           NOT NULL REFERENCES pay_transactions (id),
    reference_type        VARCHAR(32)    NOT NULL CHECK (reference_type IN ('LAB_ORDER', 'MARKETPLACE_ORDER')),
    reference_id          UUID           NOT NULL UNIQUE,
    payer_user_id         UUID           NOT NULL,
    payee_user_id         UUID           NOT NULL,
    gross_amount_vnd      NUMERIC(15, 2) NOT NULL CHECK (gross_amount_vnd >= 0),
    refunded_amount_vnd   NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (refunded_amount_vnd >= 0),
    commission_fee_vnd    NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (commission_fee_vnd >= 0),
    net_payout_amount_vnd NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (net_payout_amount_vnd >= 0),
    status                VARCHAR(32)    NOT NULL CHECK (status IN ('HELD', 'PARTIALLY_REFUNDED', 'RELEASED_TO_PAYEE', 'REFUNDED_TO_PAYER', 'FROZEN_BY_DISPUTE', 'SPLIT_SETTLED')),
    auto_release_at       TIMESTAMPTZ    NULL,
    settled_at            TIMESTAMPTZ    NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID           NULL,
    updated_by            UUID           NULL,
    version               BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pay_escrow_autorelease ON pay_escrow_holds (auto_release_at)
    WHERE status IN ('HELD', 'PARTIALLY_REFUNDED');

CREATE TABLE IF NOT EXISTS pay_wallet_ledger_entries (
    id                UUID           PRIMARY KEY,
    journal_group_id  UUID           NOT NULL,
    wallet_id         UUID           NOT NULL REFERENCES pay_wallets (id),
    payment_txn_id    UUID           NULL REFERENCES pay_transactions (id),
    entry_direction   VARCHAR(8)     NOT NULL CHECK (entry_direction IN ('DEBIT', 'CREDIT')),
    entry_type        VARCHAR(32)    NOT NULL CHECK (entry_type IN ('ESCROW_IN', 'ESCROW_RELEASE', 'PLATFORM_FEE', 'BLANK_ROLL_REFUND', 'FULL_REFUND', 'PAYOUT_LOCK', 'PAYOUT_SETTLE')),
    amount_vnd        NUMERIC(15, 2) NOT NULL CHECK (amount_vnd > 0),
    balance_after_vnd NUMERIC(15, 2) NOT NULL,
    reference_type    VARCHAR(32)    NOT NULL,
    reference_id      UUID           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pay_ledger_wallet_time ON pay_wallet_ledger_entries (wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pay_ledger_journal ON pay_wallet_ledger_entries (journal_group_id);

CREATE TABLE IF NOT EXISTS pay_refund_requests (
    id                UUID           PRIMARY KEY,
    escrow_id         UUID           NOT NULL REFERENCES pay_escrow_holds (id),
    refund_reason     VARCHAR(255)   NOT NULL,
    refund_amount_vnd NUMERIC(15, 2) NOT NULL CHECK (refund_amount_vnd > 0),
    status            VARCHAR(32)    NOT NULL CHECK (status IN ('PENDING', 'PROCESSED', 'REJECTED')),
    processed_at      TIMESTAMPTZ    NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        UUID           NULL,
    updated_by        UUID           NULL,
    version           BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_payout_requests (
    id                UUID           PRIMARY KEY,
    wallet_id         UUID           NOT NULL REFERENCES pay_wallets (id),
    requester_user_id UUID           NOT NULL,
    amount_vnd        NUMERIC(15, 2) NOT NULL CHECK (amount_vnd > 0),
    bank_code         VARCHAR(32)    NOT NULL,
    bank_account_no   VARCHAR(64)    NOT NULL,
    bank_account_name VARCHAR(128)   NOT NULL,
    status            VARCHAR(32)    NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'PROCESSING', 'COMPLETED', 'REJECTED')),
    bank_transfer_ref VARCHAR(128)   NULL,
    completed_at      TIMESTAMPTZ    NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        UUID           NULL,
    updated_by        UUID           NULL,
    version           BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_webhook_logs (
    id                    UUID         PRIMARY KEY,
    provider              VARCHAR(32)  NOT NULL,
    event_idempotency_key VARCHAR(128) NOT NULL CONSTRAINT uk_pay_webhook_idemp UNIQUE,
    raw_payload           JSONB        NOT NULL,
    signature_valid       BOOLEAN      NOT NULL,
    processing_status     VARCHAR(32)  NOT NULL,
    error_message         TEXT         NULL,
    received_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Delivery tables

CREATE TABLE IF NOT EXISTS dlv_tasks (
    id                      UUID           PRIMARY KEY,
    reference_type          VARCHAR(32)    NOT NULL,
    reference_id            UUID           NOT NULL,
    direction               VARCHAR(32)    NOT NULL CHECK (direction IN ('PICKUP_TO_LAB', 'RETURN_TO_CUSTOMER', 'C2C_SHIPPING')),
    provider                VARCHAR(32)    NOT NULL CHECK (provider IN ('AHAMOVE', 'LALAMOVE', 'INTERNAL_DRIVER', 'MOCK')),
    external_tracking_id    VARCHAR(128)   UNIQUE,
    assigned_driver_user_id UUID           NULL,
    pickup_contact_address  JSONB          NOT NULL,
    dropoff_contact_address JSONB          NOT NULL,
    shipping_fee_vnd        NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (shipping_fee_vnd >= 0),
    status                  VARCHAR(32)    NOT NULL CHECK (status IN ('CREATED', 'ASSIGNING_DRIVER', 'DRIVER_ASSIGNED', 'TIMEOUT_FAILED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNED_TO_SENDER', 'CANCELLED', 'DLV_CANCELLED')),
    proof_of_delivery_s3_key VARCHAR(512)  NULL,
    pickup_deadline_at      TIMESTAMPTZ    NULL,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              UUID           NULL,
    updated_by              UUID           NULL,
    version                 BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dlv_tasks_ref ON dlv_tasks (reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_dlv_tasks_timeout ON dlv_tasks (pickup_deadline_at)
    WHERE status IN ('CREATED', 'ASSIGNING_DRIVER');

CREATE TABLE IF NOT EXISTS dlv_tracking_logs (
    id               UUID             PRIMARY KEY,
    delivery_task_id UUID             NOT NULL REFERENCES dlv_tasks (id) ON DELETE CASCADE,
    status           VARCHAR(32)      NOT NULL,
    latitude         DOUBLE PRECISION NULL,
    longitude        DOUBLE PRECISION NULL,
    note             TEXT             NULL,
    logged_at        TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dlv_tracking_task ON dlv_tracking_logs (delivery_task_id, logged_at DESC);

CREATE TABLE IF NOT EXISTS dlv_webhook_logs (
    id                UUID         PRIMARY KEY,
    provider          VARCHAR(32)  NOT NULL,
    webhook_event_id  VARCHAR(128) NOT NULL UNIQUE,
    raw_payload       JSONB        NOT NULL,
    processing_status VARCHAR(32)  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Marketplace tables

CREATE TABLE IF NOT EXISTS mkt_listings (
    id                   UUID           PRIMARY KEY,
    seller_user_id       UUID           NOT NULL,
    slug                 VARCHAR(255)   NOT NULL UNIQUE,
    title                VARCHAR(255)   NOT NULL,
    description          TEXT           NOT NULL,
    category             VARCHAR(32)    NOT NULL CHECK (category IN ('CAMERA_BODY', 'LENS', 'FILM_ROLL', 'FLASH_ACCESSORY', 'DARKROOM_GEAR')),
    brand                VARCHAR(64)    NULL,
    model_name           VARCHAR(128)   NULL,
    condition_grade      VARCHAR(32)    NOT NULL CHECK (condition_grade IN ('MINT', 'NEAR_MINT', 'EXCELLENT', 'USER_GOOD', 'FOR_PARTS')),
    technical_specs      JSONB          NOT NULL DEFAULT '{}'::jsonb,
    price_vnd            NUMERIC(15, 2) NOT NULL CHECK (price_vnd >= 0),
    allow_price_offer    BOOLEAN        NOT NULL DEFAULT true,
    allow_swap           BOOLEAN        NOT NULL DEFAULT false,
    preferred_swap_items TEXT           NULL,
    province_code        VARCHAR(32)    NULL,
    district_code        VARCHAR(32)    NULL,
    status               VARCHAR(32)    NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RESERVED', 'SOLD', 'EXPIRED', 'HIDDEN_BY_ADMIN')),
    expires_at           TIMESTAMPTZ    NULL,
    deleted_at           TIMESTAMPTZ    NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID           NULL,
    updated_by           UUID           NULL,
    version              BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_mkt_listings_seller ON mkt_listings (seller_user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_mkt_listings_exp ON mkt_listings (expires_at) WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS mkt_listing_images (
    id         UUID         PRIMARY KEY,
    listing_id UUID         NOT NULL REFERENCES mkt_listings (id) ON DELETE CASCADE,
    s3_key     VARCHAR(512) NOT NULL,
    sort_order SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS mkt_offers (
    id                      UUID           PRIMARY KEY,
    listing_id              UUID           NOT NULL REFERENCES mkt_listings (id),
    buyer_user_id           UUID           NOT NULL,
    seller_user_id          UUID           NOT NULL,
    offer_type              VARCHAR(32)    NOT NULL CHECK (offer_type IN ('PRICE', 'SWAP', 'SWAP_WITH_CASH')),
    offered_price_vnd       NUMERIC(15, 2) NULL,
    swap_offered_listing_id UUID           NULL,
    swap_item_description   TEXT           NULL,
    cash_topup_vnd          NUMERIC(15, 2) NULL,
    status                  VARCHAR(32)    NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'COUNTERED', 'EXPIRED', 'CANCELLED', 'OFFER_PENDING', 'OFFER_COUNTERED', 'OFFER_ACCEPTED', 'OFFER_REJECTED', 'OFFER_EXPIRED')),
    expires_at              TIMESTAMPTZ    NULL,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              UUID           NULL,
    updated_by              UUID           NULL,
    version                 BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_mkt_offers_listing ON mkt_offers (listing_id, status);

CREATE TABLE IF NOT EXISTS mkt_orders (
    id                     UUID           PRIMARY KEY,
    order_code             VARCHAR(64)    NOT NULL UNIQUE,
    buyer_user_id          UUID           NOT NULL,
    seller_user_id         UUID           NOT NULL,
    accepted_offer_id      UUID           NULL REFERENCES mkt_offers (id),
    order_type             VARCHAR(32)    NOT NULL CHECK (order_type IN ('STANDARD_PURCHASE', 'SWAP_EXCHANGE')),
    item_total_vnd         NUMERIC(15, 2) NOT NULL CHECK (item_total_vnd >= 0),
    shipping_fee_vnd       NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (shipping_fee_vnd >= 0),
    total_payable_vnd      NUMERIC(15, 2) NOT NULL CHECK (total_payable_vnd >= 0),
    status                 VARCHAR(32)    NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'ESCROW_HELD_AWAITING_SHIP', 'SHIPPED', 'DELIVERED_INSPECTING', 'COMPLETED', 'CANCELLED', 'DISPUTED', 'REFUNDED')),
    inspection_deadline_at TIMESTAMPTZ    NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by             UUID           NULL,
    updated_by             UUID           NULL,
    version                BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_mkt_orders_buyer ON mkt_orders (buyer_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mkt_orders_seller ON mkt_orders (seller_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS mkt_order_items (
    id               UUID           PRIMARY KEY,
    market_order_id  UUID           NOT NULL REFERENCES mkt_orders (id) ON DELETE CASCADE,
    listing_id       UUID           NOT NULL REFERENCES mkt_listings (id),
    title_snapshot   VARCHAR(255)   NOT NULL,
    agreed_price_vnd NUMERIC(15, 2) NOT NULL CHECK (agreed_price_vnd >= 0)
);

CREATE TABLE IF NOT EXISTS mkt_seller_reviews (
    id                UUID        PRIMARY KEY,
    market_order_id   UUID        NOT NULL UNIQUE REFERENCES mkt_orders (id),
    reviewer_user_id  UUID        NOT NULL,
    seller_user_id    UUID        NOT NULL,
    rating            SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    item_as_described BOOLEAN     NOT NULL DEFAULT true,
    comment           TEXT        NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID        NULL,
    updated_by        UUID        NULL,
    version           BIGINT      NOT NULL DEFAULT 0
);

-- Messaging tables

CREATE TABLE IF NOT EXISTS msg_conversations (
    id                   UUID        PRIMARY KEY,
    context_type         VARCHAR(32) NOT NULL CHECK (context_type IN ('MARKET_LISTING', 'MARKET_ORDER', 'LAB_ORDER')),
    context_reference_id UUID        NOT NULL,
    last_message_at      TIMESTAMPTZ NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID        NULL,
    updated_by           UUID        NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_msg_conv_context ON msg_conversations (context_type, context_reference_id);

CREATE TABLE IF NOT EXISTS msg_participants (
    id              UUID        PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES msg_conversations (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL,
    last_read_at    TIMESTAMPTZ NULL,
    unread_count    INT         NOT NULL DEFAULT 0,
    UNIQUE (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_msg_participants_user ON msg_participants (user_id);

CREATE TABLE IF NOT EXISTS msg_messages (
    id               UUID        PRIMARY KEY,
    conversation_id  UUID        NOT NULL REFERENCES msg_conversations (id) ON DELETE CASCADE,
    sender_user_id   UUID        NOT NULL,
    message_type     VARCHAR(32) NOT NULL CHECK (message_type IN ('TEXT', 'IMAGE', 'OFFER_CARD', 'SYSTEM_NOTICE')),
    content          TEXT        NOT NULL,
    metadata_payload JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_msg_messages_conv_time ON msg_messages (conversation_id, created_at DESC);

-- Community tables

CREATE TABLE IF NOT EXISTS com_expert_articles (
    id               UUID         PRIMARY KEY,
    author_expert_id UUID         NOT NULL,
    slug             VARCHAR(255) NOT NULL UNIQUE,
    title            VARCHAR(255) NOT NULL,
    content_markdown TEXT         NOT NULL,
    category         VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETRACTED')),
    approved_for_rag BOOLEAN      NOT NULL DEFAULT false,
    deleted_at       TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID         NULL,
    updated_by       UUID         NULL,
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS com_discussion_posts (
    id                         UUID         PRIMARY KEY,
    author_user_id             UUID         NOT NULL,
    post_type                  VARCHAR(32)  NOT NULL CHECK (post_type IN ('DISCUSSION', 'QUESTION_QA', 'PHOTO_SHOWCASE')),
    title                      VARCHAR(255) NOT NULL,
    body_markdown              TEXT         NOT NULL,
    attached_archive_frame_id  UUID         NULL,
    accepted_answer_comment_id UUID         NULL,
    moderation_status          VARCHAR(32)  NOT NULL DEFAULT 'APPROVED' CHECK (moderation_status IN ('PENDING', 'APPROVED', 'HIDDEN', 'REJECTED')),
    deleted_at                 TIMESTAMPTZ  NULL,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                 UUID         NULL,
    updated_by                 UUID         NULL,
    version                    BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS com_workshops (
    id                   UUID           PRIMARY KEY,
    organizer_user_id    UUID           NOT NULL,
    workshop_type        VARCHAR(32)    NOT NULL CHECK (workshop_type IN ('WORKSHOP', 'PHOTOWALK')),
    title                VARCHAR(255)   NOT NULL,
    description          TEXT           NULL,
    location_address     VARCHAR(512)   NULL,
    start_time           TIMESTAMPTZ    NOT NULL,
    end_time             TIMESTAMPTZ    NULL,
    max_participants     INT            NOT NULL CHECK (max_participants > 0),
    current_participants INT            NOT NULL DEFAULT 0 CHECK (current_participants >= 0),
    ticket_price_vnd     NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (ticket_price_vnd >= 0),
    status               VARCHAR(32)    NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'FULL', 'COMPLETED', 'CANCELLED')),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID           NULL,
    updated_by           UUID           NULL,
    version              BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS com_workshop_registrations (
    id            UUID        PRIMARY KEY,
    workshop_id   UUID        NOT NULL REFERENCES com_workshops (id),
    user_id       UUID        NOT NULL,
    status        VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ATTENDED', 'CANCELLED')),
    checkin_code  VARCHAR(64) NOT NULL UNIQUE,
    checked_in_at TIMESTAMPTZ NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID        NULL,
    updated_by    UUID        NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (workshop_id, user_id)
);

CREATE TABLE IF NOT EXISTS com_comments (
    id                UUID        PRIMARY KEY,
    target_type       VARCHAR(32) NOT NULL CHECK (target_type IN ('EXPERT_ARTICLE', 'DISCUSSION_POST', 'WORKSHOP')),
    target_id         UUID        NOT NULL,
    parent_comment_id UUID        NULL REFERENCES com_comments (id),
    author_user_id    UUID        NOT NULL,
    content           TEXT        NOT NULL,
    deleted_at        TIMESTAMPTZ NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID        NULL,
    updated_by        UUID        NULL,
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_com_comments_target ON com_comments (target_type, target_id, created_at ASC);

CREATE TABLE IF NOT EXISTS com_reactions (
    id            UUID        PRIMARY KEY,
    target_type   VARCHAR(32) NOT NULL CHECK (target_type IN ('EXPERT_ARTICLE', 'DISCUSSION_POST', 'COMMENT', 'WORKSHOP')),
    target_id     UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    reaction_type VARCHAR(32) NOT NULL CHECK (reaction_type IN ('LIKE', 'BOOKMARK', 'HELPFUL')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (target_type, target_id, user_id, reaction_type)
);

CREATE TABLE IF NOT EXISTS com_content_reports (
    id               UUID        PRIMARY KEY,
    reporter_user_id UUID        NOT NULL,
    target_type      VARCHAR(32) NOT NULL CHECK (target_type IN ('EXPERT_ARTICLE', 'DISCUSSION_POST', 'COMMENT', 'MARKETPLACE_LISTING', 'LAB_REVIEW')),
    target_id        UUID        NOT NULL,
    reason           VARCHAR(64) NOT NULL,
    details          TEXT        NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID        NULL,
    updated_by       UUID        NULL,
    version          BIGINT      NOT NULL DEFAULT 0
);

-- Notification tables

CREATE TABLE IF NOT EXISTS ntf_notifications (
    id                UUID         PRIMARY KEY,
    recipient_user_id UUID         NOT NULL,
    source_event_id   UUID         NULL,
    notification_type VARCHAR(64)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              TEXT         NOT NULL,
    action_payload    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    is_read           BOOLEAN      NOT NULL DEFAULT false,
    read_at           TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ntf_user_unread ON ntf_notifications (recipient_user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ntf_source_event ON ntf_notifications (recipient_user_id, source_event_id)
    WHERE source_event_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ntf_device_tokens (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL,
    device_token VARCHAR(512) NOT NULL UNIQUE,
    platform     VARCHAR(32)  NOT NULL CHECK (platform IN ('WEB', 'ANDROID', 'IOS')),
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ntf_device_user ON ntf_device_tokens (user_id) WHERE is_active = true;

CREATE TABLE IF NOT EXISTS ntf_preferences (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NOT NULL UNIQUE,
    in_app_enabled   BOOLEAN     NOT NULL DEFAULT true,
    email_enabled    BOOLEAN     NOT NULL DEFAULT true,
    push_enabled     BOOLEAN     NOT NULL DEFAULT true,
    muted_categories JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID        NULL,
    updated_by       UUID        NULL,
    version          BIGINT      NOT NULL DEFAULT 0
);
