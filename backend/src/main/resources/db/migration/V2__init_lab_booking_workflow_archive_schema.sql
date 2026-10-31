-- V2__init_lab_booking_workflow_archive_schema.sql
-- Migration for Film Lab, Booking, Workflow, and Digital Archive (Cluster B & C)

-- 1. Film Lab Tables
CREATE TABLE IF NOT EXISTS lab_film_labs (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             tenant_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS lab_members (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           lab_id BIGINT NOT NULL,
                                           user_id BIGINT NOT NULL,
                                           role VARCHAR(50) NOT NULL,
    CONSTRAINT fk_lab_members_lab FOREIGN KEY (lab_id) REFERENCES lab_film_labs(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS lab_service_packages (
                                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    lab_id BIGINT NOT NULL,
                                                    package_name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_lab_services_lab FOREIGN KEY (lab_id) REFERENCES lab_film_labs(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS lab_capacity_rules (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  lab_id BIGINT NOT NULL,
                                                  max_daily_rolls INT NOT NULL,
                                                  CONSTRAINT fk_lab_capacity_lab FOREIGN KEY (lab_id) REFERENCES lab_film_labs(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS lab_film_stock_catalog (
                                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                      lab_id BIGINT NOT NULL,
                                                      film_name VARCHAR(255) NOT NULL,
    iso INT NOT NULL,
    exposures INT NOT NULL,
    CONSTRAINT fk_film_stock_lab FOREIGN KEY (lab_id) REFERENCES lab_film_labs(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS lab_reviews (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           lab_id BIGINT NOT NULL,
                                           user_id BIGINT NOT NULL,
                                           rating INT CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lab_reviews_lab FOREIGN KEY (lab_id) REFERENCES lab_film_labs(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS lab_daily_analytics (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   lab_id BIGINT NOT NULL,
                                                   analytics_date DATE NOT NULL,
                                                   total_orders INT DEFAULT 0,
                                                   total_revenue DECIMAL(12, 2) DEFAULT 0.00,
    CONSTRAINT fk_lab_analytics_lab FOREIGN KEY (lab_id) REFERENCES lab_film_labs(id) ON DELETE CASCADE
    );

-- 2. Booking Tables
CREATE TABLE IF NOT EXISTS bkg_lab_orders (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              tenant_id VARCHAR(50) NOT NULL,
    lab_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    total_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS bkg_lab_order_items (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   order_id BIGINT NOT NULL,
                                                   service_package_id BIGINT NOT NULL,
                                                   quantity INT NOT NULL,
                                                   subtotal DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES bkg_lab_orders(id) ON DELETE CASCADE
    );

-- 3. Workflow Tables
CREATE TABLE IF NOT EXISTS wfl_film_rolls (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              order_id BIGINT NOT NULL,
                                              roll_code VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(50) DEFAULT 'RECEIVED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_film_rolls_order FOREIGN KEY (order_id) REFERENCES bkg_lab_orders(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS wfl_film_roll_status_logs (
                                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                         roll_id BIGINT NOT NULL,
                                                         previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    changed_by BIGINT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_status_logs_roll FOREIGN KEY (roll_id) REFERENCES wfl_film_rolls(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS wfl_scan_upload_batches (
                                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                       roll_id BIGINT NOT NULL,
                                                       batch_token VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scan_batches_roll FOREIGN KEY (roll_id) REFERENCES wfl_film_rolls(id) ON DELETE CASCADE
    );

-- 4. Digital Archive Tables
CREATE TABLE IF NOT EXISTS arc_albums (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          user_id BIGINT NOT NULL,
                                          album_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS arc_rolls (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         album_id BIGINT NOT NULL,
                                         roll_name VARCHAR(255) NOT NULL,
    CONSTRAINT fk_arc_rolls_album FOREIGN KEY (album_id) REFERENCES arc_albums(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS arc_frames (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          roll_id BIGINT NOT NULL,
                                          frame_number INT NOT NULL,
                                          image_url VARCHAR(500) NOT NULL,
    CONSTRAINT fk_arc_frames_roll FOREIGN KEY (roll_id) REFERENCES arc_rolls(id) ON DELETE CASCADE
    );

-- Indexes for performance
CREATE INDEX idx_bkg_orders_tenant ON bkg_lab_orders(tenant_id);
CREATE INDEX idx_wfl_rolls_code ON wfl_film_rolls(roll_code);
CREATE INDEX idx_arc_frames_roll ON arc_frames(roll_id, frame_number);