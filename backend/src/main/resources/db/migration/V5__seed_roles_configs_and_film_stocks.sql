-- ==============================================================================
-- V5: MIGRATION SEED MASTER DATA 
-- Chú ý: Sử dụng ON CONFLICT DO NOTHING để đảm bảo chạy an toàn nhiều lần (Idempotent)
-- ==============================================================================

-- ---------------------------------------------------------
-- 1. SEED 5 ROLES (Sử dụng bảng iam_roles từ V1 Cụm A)
-- ---------------------------------------------------------
INSERT INTO iam_roles (id, code, name, description) VALUES 
(gen_random_uuid(), 'CUSTOMER', 'Customer', 'Người dùng hệ thống, khách hàng đặt dịch vụ Lab và mua bán Market'),
(gen_random_uuid(), 'LAB_OWNER', 'Lab Owner', 'Chủ Lab, người quản lý lab_film_labs và xử lý đơn hàng bkg_lab_orders'),
(gen_random_uuid(), 'EXPERT', 'Expert', 'Chuyên gia đăng bài com_expert_articles và chấm điểm QC'),
(gen_random_uuid(), 'DELIVERY_PARTNER', 'Delivery Partner', 'Đối tác vận chuyển xử lý dlv_tasks'),
(gen_random_uuid(), 'ADMIN', 'Admin', 'Quản trị viên hệ thống, quản lý adm_disputes và adm_moderation_tickets')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------
-- 2. SEED 4 CONFIGURATIONS (Sử dụng bảng cfg_fee_configurations từ V1 Cụm A)
-- ---------------------------------------------------------
INSERT INTO cfg_fee_configurations (id, config_key, config_name, fee_type, fee_value, status) VALUES 
(gen_random_uuid(), 'PLATFORM_FEE_PCT', 'Phí nền tảng (Platform Fee)', 'PERCENTAGE', 5.00, 'ACTIVE'),
(gen_random_uuid(), 'ESCROW_FEE_FIXED', 'Phí giữ tiền an toàn (Escrow)', 'FIXED', 10000.00, 'ACTIVE'),
(gen_random_uuid(), 'DELIVERY_BASE_FEE', 'Phí vận chuyển cơ bản', 'FIXED', 25000.00, 'ACTIVE'),
(gen_random_uuid(), 'VAT_TAX_PCT', 'Thuế VAT', 'PERCENTAGE', 8.00, 'ACTIVE')
ON CONFLICT (config_key) DO NOTHING;

-- ---------------------------------------------------------
-- 3. SEED SYSTEM WALLETS (Sử dụng bảng pay_wallets từ V3 Cụm D)
-- ---------------------------------------------------------
-- Ghi chú: Ví hệ thống không gán cho user cụ thể nên owner_id để NULL 
INSERT INTO pay_wallets (id, owner_id, wallet_type, balance, currency, status) VALUES 
(gen_random_uuid(), NULL, 'SYSTEM_ESCROW', 0.00, 'VND', 'ACTIVE'),
(gen_random_uuid(), NULL, 'SYSTEM_REVENUE', 0.00, 'VND', 'ACTIVE');
-- Nếu bảng pay_wallets có UNIQUE index (wallet_type) WHERE owner_id IS NULL, 
-- bạn có thể thêm: ON CONFLICT (wallet_type) DO NOTHING;

-- ---------------------------------------------------------
-- 4. SEED DANH MỤC LAB FILM STOCK (Sử dụng bảng lab_film_stock_catalog từ V2 Cụm B)
-- ---------------------------------------------------------
INSERT INTO lab_film_stock_catalog (id, code, brand, name, iso, format, type) VALUES 
(gen_random_uuid(), 'KODAK_GOLD_200', 'Kodak', 'Kodak Gold 200', 200, '35mm', 'COLOR_NEGATIVE'),
(gen_random_uuid(), 'KODAK_PORTRA_400', 'Kodak', 'Kodak Portra 400', 400, '35mm', 'COLOR_NEGATIVE'),
(gen_random_uuid(), 'KODAK_ULTRAMAX_400', 'Kodak', 'Kodak UltraMax 400', 400, '35mm', 'COLOR_NEGATIVE'),
(gen_random_uuid(), 'FUJI_C200', 'Fujifilm', 'Fujicolor C200', 200, '35mm', 'COLOR_NEGATIVE'),
(gen_random_uuid(), 'FUJI_SUPERIA_400', 'Fujifilm', 'Fujicolor Superia X-TRA 400', 400, '35mm', 'COLOR_NEGATIVE'),
(gen_random_uuid(), 'ILFORD_HP5_PLUS', 'Ilford', 'Ilford HP5 Plus 400', 400, '35mm', 'BLACK_AND_WHITE'),
(gen_random_uuid(), 'KODAK_VISION3_250D', 'Kodak', 'Kodak Vision3 250D', 250, '35mm', 'CINEMA_COLOR'),
(gen_random_uuid(), 'CINESTILL_800T', 'CineStill', 'CineStill 800T', 800, '35mm', 'COLOR_NEGATIVE')
ON CONFLICT (code) DO NOTHING;