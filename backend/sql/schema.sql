CREATE DATABASE IF NOT EXISTS second_hand_trade
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE second_hand_trade;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_no VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  real_name VARCHAR(64),
  department VARCHAR(128),
  enrollment_year INT,
  campus VARCHAR(64),
  email VARCHAR(128) UNIQUE,
  phone VARCHAR(32),
  avatar_url VARCHAR(500),
  verified_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
  credit_score INT NOT NULL DEFAULT 100,
  status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  last_login_at DATETIME,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_users_campus (campus),
  INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_privacy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  phone_visible TINYINT(1) NOT NULL DEFAULT 0,
  wechat_visible TINYINT(1) NOT NULL DEFAULT 0,
  qq VARCHAR(64),
  wechat VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
  status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  last_login_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL UNIQUE,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS category_tags (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_category_tags_category_name (category_id, name),
  INDEX idx_category_tags_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  seller_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  original_price DECIMAL(10,2),
  condition_level VARCHAR(32) NOT NULL,
  campus VARCHAR(64) NOT NULL,
  dormitory VARCHAR(128),
  trade_place VARCHAR(128),
  trade_modes VARCHAR(128) NOT NULL DEFAULT 'OFFLINE',
  status VARCHAR(32) NOT NULL DEFAULT 'ON_SALE',
  swap_supported TINYINT(1) NOT NULL DEFAULT 0,
  view_count INT NOT NULL DEFAULT 0,
  favorite_count INT NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_items_seller_id (seller_id),
  INDEX idx_items_category_id (category_id),
  INDEX idx_items_campus (campus),
  INDEX idx_items_status (status),
  INDEX idx_items_created_at (created_at),
  INDEX idx_items_price (price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS item_images (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_item_images_item_id (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS favorites (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_favorites_user_item (user_id, item_id),
  INDEX idx_favorites_item_id (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS item_comments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT,
  content VARCHAR(500) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_item_comments_item_id (item_id),
  INDEX idx_item_comments_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  item_id BIGINT NOT NULL,
  buyer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  trade_mode VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
  trade_code VARCHAR(32),
  trade_qr_url VARCHAR(500),
  buyer_message VARCHAR(500),
  cancel_reason VARCHAR(500),
  completed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_orders_buyer_id (buyer_id),
  INDEX idx_orders_seller_id (seller_id),
  INDEX idx_orders_status (status),
  INDEX idx_orders_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_status_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  operator_type VARCHAR(32) NOT NULL,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order_status_logs_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(64) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'INIT',
  provider_trade_no VARCHAR(128),
  payment_url VARCHAR(1000),
  qr_url VARCHAR(1000),
  request_payload TEXT,
  response_payload TEXT,
  paid_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_payments_order_id (order_id),
  INDEX idx_payments_provider_status (provider, status),
  INDEX idx_payments_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  reviewer_id BIGINT NOT NULL,
  target_user_id BIGINT NOT NULL,
  rating INT NOT NULL,
  content VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_reviews_order_reviewer (order_id, reviewer_id),
  INDEX idx_reviews_target_user_id (target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  buyer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  last_message VARCHAR(500),
  last_message_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chats_item_buyer_seller (item_id, buyer_id, seller_id),
  INDEX idx_chats_buyer_id (buyer_id),
  INDEX idx_chats_seller_id (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  chat_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
  content TEXT,
  image_url VARCHAR(500),
  item_id BIGINT,
  filtered TINYINT(1) NOT NULL DEFAULT 0,
  read_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_chat_messages_chat_id_created_at (chat_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wanted_posts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT,
  category_id BIGINT,
  campus VARCHAR(64) NOT NULL,
  budget_min DECIMAL(10,2),
  budget_max DECIMAL(10,2),
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_wanted_posts_user_id (user_id),
  INDEX idx_wanted_posts_campus (campus),
  INDEX idx_wanted_posts_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT,
  category_id BIGINT,
  campus VARCHAR(64) NOT NULL,
  budget_min DECIMAL(10,2),
  budget_max DECIMAL(10,2),
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_purchases_user_id (user_id),
  INDEX idx_purchases_category_id (category_id),
  INDEX idx_purchases_campus (campus),
  INDEX idx_purchases_status (status),
  INDEX idx_purchases_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS swap_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(64) NOT NULL UNIQUE,
  requester_id BIGINT NOT NULL,
  target_item_id BIGINT NOT NULL,
  offered_item_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  message VARCHAR(1000),
  handled_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_swap_requests_requester_id (requester_id),
  INDEX idx_swap_requests_target_item_id (target_item_id),
  INDEX idx_swap_requests_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exchanges (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  exchange_no VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  target_item_id BIGINT,
  target_category_id BIGINT,
  expected_title VARCHAR(100),
  description TEXT,
  campus VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_exchanges_user_id (user_id),
  INDEX idx_exchanges_item_id (item_id),
  INDEX idx_exchanges_target_item_id (target_item_id),
  INDEX idx_exchanges_target_category_id (target_category_id),
  INDEX idx_exchanges_status (status),
  INDEX idx_exchanges_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  report_type VARCHAR(64) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  handled_by BIGINT,
  handled_at DATETIME,
  result_remark VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reports_target (target_type, target_id),
  INDEX idx_reports_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS disputes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dispute_no VARCHAR(64) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  applicant_id BIGINT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  evidence_urls TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  handled_by BIGINT,
  handled_at DATETIME,
  result_remark VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_disputes_order_id (order_id),
  INDEX idx_disputes_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sensitive_words (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  word VARCHAR(128) NOT NULL UNIQUE,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(150) NOT NULL,
  content TEXT NOT NULL,
  scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL',
  campus VARCHAR(64),
  popup_enabled TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  published_at DATETIME,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_announcements_status (status),
  INDEX idx_announcements_scope (scope_type, campus)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type VARCHAR(64) NOT NULL,
  title VARCHAR(150) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  read_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_notifications_user_read (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(128) NOT NULL UNIQUE,
  setting_value TEXT NOT NULL,
  description VARCHAR(500),
  updated_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS files (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  file_type VARCHAR(32) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  storage_key VARCHAR(500) NOT NULL,
  url VARCHAR(500) NOT NULL,
  size_bytes BIGINT NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_files_owner_id (owner_id),
  INDEX idx_files_file_type (file_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id BIGINT NOT NULL,
  action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT,
  detail TEXT,
  ip VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_logs_admin_id (admin_id),
  INDEX idx_audit_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
