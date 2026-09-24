# Database initialization

This directory intentionally contains no generated schema or production data.
The repository's `migration.sql` is an incremental migration and must not be used to initialize an empty database.

Before the first `docker compose up`, place the project's original full database dump here as `001-sky-take-out.sql`. MySQL executes files in this directory only when the `mysql-data` volume is empty.

Do not commit dumps containing real users, addresses, orders, credentials, or other private data.
