-- ==============================================================================
-- V5: MIGRATION SEED MASTER DATA 
-- Đã chuẩn hóa chính xác theo schema V1, V2, V3 
-- ==============================================================================

-- ---------------------------------------------------------
-- 1. SEED 5 ROLES (Bảng iam_roles)
-- ---------------------------------------------------------
INSERT INTO iam_roles (id, code, name, description) VALUES 
(gen_random_uuid(), 'CUSTOMER', 'Customer', 'Khách hàng và người dùng hệ thống'),
(gen_random_uuid(), 'LAB_OWNER', 'Lab Owner', 'Chủ Lab xử lý đơn hàng'),
(gen_random_uuid(), 'EXPERT', 'Expert', 'Chuyên gia QC và bài viết'),
(gen_random_uuid(), 'DELIVERY_PARTNER', 'Delivery Partner', 'Đối tác vận chuyển'),
(gen_random_uuid(), 'ADMIN', 'Admin', 'Quản trị viên hệ thống')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------
-- 2. SEED 4 FEE CONFIGURATIONS (Bảng cfg_fee_configurations)
-- Map với 4 scope: LAB_ORDER, MARKETPLACE_SALE, MARKETPLACE_SWAP, WORKSHOP_TICKET
-- ---------------------------------------------------------
INSERT INTO cfg_fee_configurations (id, fee_scope, commission_rate, fixed_fee_vnd, is_active) VALUES 
(gen_random_uuid(), 'LAB_ORDER', 0.0500, 0, true),           -- Phí nền tảng Lab 5%
(gen_random_uuid(), 'MARKETPLACE_SALE', 0.0500, 0, true),    -- Phí bán Market 5%
(gen_random_uuid(), 'MARKETPLACE_SWAP', 0.0000, 10000.00, true), -- Phí cố định đổi đồ
(gen_random_uuid(), 'WORKSHOP_TICKET', 0.0800, 0, true)      -- Phí vé Workshop 8% (VAT)
ON CONFLICT (fee_scope) DO NOTHING;

-- ---------------------------------------------------------
-- 3. SEED SYSTEM WALLETS (Bảng pay_wallets)
-- Sử dụng mệnh đề SELECT WHERE NOT EXISTS để tránh lỗi duplicate cho system wallet
-- ---------------------------------------------------------
INSERT INTO pay_wallets (id, owner_user_id, wallet_type, available_balance_vnd, locked_balance_vnd, currency)
SELECT gen_random_uuid(), NULL, 'SYSTEM_ESCROW', 0.00, 0.00, 'VND'
WHERE NOT EXISTS (SELECT 1 FROM pay_wallets WHERE wallet_type = 'SYSTEM_ESCROW');

INSERT INTO pay_wallets (id, owner_user_id, wallet_type, available_balance_vnd, locked_balance_vnd, currency)
SELECT gen_random_uuid(), NULL, 'SYSTEM_REVENUE', 0.00, 0.00, 'VND'
WHERE NOT EXISTS (SELECT 1 FROM pay_wallets WHERE wallet_type = 'SYSTEM_REVENUE');

-- ---------------------------------------------------------
-- 4. SEED DANH MỤC LAB FILM STOCK (Bảng lab_film_stock_catalog)
-- Định dạng chuẩn: film_format ('FMT_135'), process_type ('C41', 'BW', 'ECN2')
-- ---------------------------------------------------------
INSERT INTO lab_film_stock_catalog (id, code, brand, name, iso, film_format, process_type) VALUES 
(gen_random_uuid(), 'KODAK_GOLD_200', 'Kodak', 'Kodak Gold 200', 200, 'FMT_135', 'C41'),
(gen_random_uuid(), 'KODAK_PORTRA_400', 'Kodak', 'Kodak Portra 400', 400, 'FMT_135', 'C41'),
(gen_random_uuid(), 'KODAK_ULTRAMAX_400', 'Kodak', 'Kodak UltraMax 400', 400, 'FMT_135', 'C41'),
(gen_random_uuid(), 'FUJI_C200', 'Fujifilm', 'Fujicolor C200', 200, 'FMT_135', 'C41'),
(gen_random_uuid(), 'FUJI_SUPERIA_400', 'Fujifilm', 'Fujicolor Superia X-TRA 400', 400, 'FMT_135', 'C41'),
(gen_random_uuid(), 'ILFORD_HP5_PLUS', 'Ilford', 'Ilford HP5 Plus 400', 400, 'FMT_135', 'BW'),
(gen_random_uuid(), 'KODAK_VISION3_250D', 'Kodak', 'Kodak Vision3 250D', 250, 'FMT_135', 'ECN2'),
(gen_random_uuid(), 'CINESTILL_800T', 'CineStill', 'CineStill 800T', 800, 'FMT_135', 'C41')
