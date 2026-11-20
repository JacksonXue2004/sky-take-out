-- Schema changes made on top of the original course database (001-demo-seed.sql).
-- MySQL runs this file by itself only when it initializes an empty data volume.
-- To update an existing database, run it once by hand; see README.md in this directory.

-- Order numbers used to come from System.currentTimeMillis(), so two orders placed in the
-- same millisecond could share one. Numbers are now random UUIDs, and the database refuses duplicates.
ALTER TABLE orders ADD UNIQUE KEY uk_orders_number (number);

-- A retried or double-clicked checkout sends the same Idempotency-Key header, and the second request
-- gets the first order back instead of creating another. MySQL does not compare NULLs in a unique
-- key, so orders placed without a key are unaffected.
ALTER TABLE orders
    ADD COLUMN idempotency_key varchar(64) DEFAULT NULL,
    ADD UNIQUE KEY uk_orders_user_idempotency_key (user_id, idempotency_key);

-- Stripe refunds name the payment by its PaymentIntent id, which the checkout webhook now stores.
-- Orders paid through the simulated checkout have none.
ALTER TABLE orders ADD COLUMN payment_intent_id varchar(255) DEFAULT NULL;

-- Every Stripe event that passed the signature check. Stripe delivers events at least once, so the
-- unique event_id turns a redelivery into a no-op. Events that are signed but unusable (unknown
-- order, wrong amount, malformed) stay here as REJECTED for someone to review.
CREATE TABLE webhook_event (
  id bigint NOT NULL AUTO_INCREMENT,
  event_id varchar(255) DEFAULT NULL,
  type varchar(100) DEFAULT NULL,
  status varchar(20) NOT NULL,
  order_number varchar(50) DEFAULT NULL,
  error varchar(500) DEFAULT NULL,
  payload mediumtext NOT NULL,
  attempts int NOT NULL DEFAULT 1,
  received_at datetime NOT NULL,
  processed_at datetime DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_webhook_event_event_id (event_id),
  KEY idx_webhook_event_status (status, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
