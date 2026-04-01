-- V1__create_webhook_events.sql
CREATE TABLE IF NOT EXISTS webhook_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider VARCHAR(32) NOT NULL,           -- STRIPE | RAZORPAY
  event_id VARCHAR(128) NOT NULL,          -- provider event id (unique per provider)
  event_type VARCHAR(128),                 -- e.g., payment_intent.succeeded
  payload LONGTEXT NOT NULL,               -- full JSON payload
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP NULL DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, SUCCESS, FAILED, MANUAL_REVIEW
  error_message VARCHAR(1024) NULL,
  created_by VARCHAR(64) NULL,
  CONSTRAINT uq_provider_event UNIQUE (provider, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- optional indexes for admin queries
CREATE INDEX idx_webhook_status ON webhook_events(status);
CREATE INDEX idx_webhook_received_at ON webhook_events(received_at);
