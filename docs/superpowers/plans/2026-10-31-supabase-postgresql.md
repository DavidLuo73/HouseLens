# Supabase PostgreSQL Implementation Plan

**Goal:** Make API, Worker and maintenance tools persist through Supabase PostgreSQL.

**Architecture:** Retain EF Core and existing application services; replace SQLite with Npgsql 10.0.3. Use a new PostgreSQL initial migration and the private `houselens` search path. Keep the SQLite file untouched. The user explicitly chose not to import old house data; future crawls populate PostgreSQL.

**Constraints:** No credentials in tracked files. Verify TLS using the Supabase root CA. Do not delete remote application data. Integration tests create and remove only their own randomly named schemas. Keep frontend API contracts unchanged.

- [x] Replace provider, configuration, SQLite PRAGMAs and migrations; initialize API and standalone Worker.
- [x] Convert integration fixtures to real PostgreSQL; cover decimals, UTC, uniqueness, relationships and nullable sorting.
- [x] Convert reset-data SQL and documentation, retaining maintenance modes and transactional writes.
- [x] Restore/build, run unit and integration tests; inspect and provision only the application's remote schema; verify API read/write persistence.
- [x] Review the diff and report any remaining limitations. No SQLite data import is included, per user instruction.

Validation notes: Release build has zero warnings/errors. API and Worker started against the remote database; API write was verified by a separate PostgreSQL connection and the test row removed. Original SQLite file remains untouched. Initial PostgreSQL tests reproduced null ordering and local-only timing assumptions; fixed with explicit ordering and a remote notification budget. Hosted server has 60 connection slots, so test fixtures must run within a small connection budget.

Final verification: 159 unit tests and 58 PostgreSQL integration tests passed. Tests run one fixture collection at a time, with at most two pooled connections per fixture. No live crawlers or actual LINE messages were invoked. Supabase contains 11 application tables plus migration history, 8 default district settings and zero properties.
