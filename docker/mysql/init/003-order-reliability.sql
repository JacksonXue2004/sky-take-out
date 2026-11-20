-- Schema changes made on top of the original course database (001-demo-seed.sql).
-- MySQL runs this file by itself only when it initializes an empty data volume.
-- To update an existing database, run it once by hand; see README.md in this directory.

-- Order numbers used to come from System.currentTimeMillis(), so two orders placed in the
-- same millisecond could share one. Numbers are now random UUIDs, and the database refuses duplicates.
ALTER TABLE orders ADD UNIQUE KEY uk_orders_number (number);
