-- Schema changes made on top of the original course database (001-demo-seed.sql).
-- MySQL runs this file by itself only when it initializes an empty data volume.
-- To update an existing database, run it once by hand; see README.md in this directory.

-- Order numbers used to come from System.currentTimeMillis(), so two orders placed in the
-- same millisecond could share one. Numbers are now random UUIDs, and the database refuses duplicates.
ALTER TABLE orders ADD UNIQUE KEY uk_orders_number (number);

-- A retried or double-clicked checkout sends the same Idempotency-Key header, and the second request
-- gets the first order back instead of creating another. MySQL does not compare NULLs in a unique
-- key, so orders placed without a key are unaffected. Two statements, because TiDB cannot index a
-- column added in the same ALTER TABLE.
ALTER TABLE orders ADD COLUMN idempotency_key varchar(64) DEFAULT NULL;
ALTER TABLE orders ADD UNIQUE KEY uk_orders_user_idempotency_key (user_id, idempotency_key);

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

-- Refund outbox. Cancelling or rejecting a paid order no longer calls Stripe inside the database
-- transaction; the transaction writes a task here, and RefundWorker sends it afterwards with retries.
-- One task per order (the unique order_id), so an order can never be refunded twice.
CREATE TABLE refund_task (
  id bigint NOT NULL AUTO_INCREMENT,
  order_id bigint NOT NULL,
  order_number varchar(50) NOT NULL,
  payment_intent_id varchar(255) NOT NULL,
  amount decimal(10,2) NOT NULL,
  status varchar(20) NOT NULL,
  attempts int NOT NULL DEFAULT 0,
  next_attempt_at datetime NOT NULL,
  last_error varchar(500) DEFAULT NULL,
  stripe_refund_id varchar(255) DEFAULT NULL,
  created_at datetime NOT NULL,
  updated_at datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_task_order_id (order_id),
  KEY idx_refund_task_due (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Indexes for the queries every request runs; the original schema had primary keys only.
-- Without idx_shopping_cart_user, the checkout's "delete from shopping_cart where user_id = ?" scans
-- the whole table and InnoDB locks every row it scans, so two customers checking out at the same
-- time block each other.
ALTER TABLE orders
    ADD KEY idx_orders_user_time (user_id, order_time),
    ADD KEY idx_orders_status_time (status, order_time);
ALTER TABLE order_detail ADD KEY idx_order_detail_order (order_id);
ALTER TABLE shopping_cart ADD KEY idx_shopping_cart_user (user_id);
ALTER TABLE address_book ADD KEY idx_address_book_user (user_id);
