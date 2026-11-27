-- The shop's open or closed flag. It used to live only in Redis, so a Redis restart, which empties a
-- free Render Key Value instance, quietly reopened a closed shop. The table is now the record: placing
-- an order reads it, and Redis only keeps a copy for the customer pages. One row, id 1.
CREATE TABLE shop (
  id bigint NOT NULL,
  status int NOT NULL DEFAULT 1 COMMENT '1 open, 0 closed',
  update_time datetime DEFAULT NULL,
  update_user bigint DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO shop (id, status, update_time, update_user) VALUES (1, 1, NOW(), 1);
