# Database backup and isolated-restore report

- Target: development-only project `vhckkmzyabbsbgjuhjmq`
- Verified: 9 November 2026, 18:07 CAT
- Status: **BLOCKED — no source archive exists and no isolated source restore has passed**

## Current evidence

Supabase's read-only Management API reports zero accessible backups and PITR disabled. WAL/provider internals do not constitute an operator-recoverable backup. This managed executor has Docker but no native PostgreSQL client, no database password and no supported raw-TCP route to the public Session pooler. HTTP management access cannot substitute for `pg_dump`/`pg_restore` coverage.

The reviewed operator path is [WINDOWS_SUPABASE_BACKUP.md](WINDOWS_SUPABASE_BACKUP.md). The Windows machine has already demonstrated PowerShell 7.6.9, Linux Docker and PostgreSQL 17.11 tools through the pinned Supabase image. Remaining capture prerequisites are the existing database password, trusted CA certificate and a private encrypted folder outside Git.

## Required pass evidence

1. `verify-full` TLS and confirmed database identity using the Session pooler.
2. Stable source before/after inventories while development writes are paused.
3. Custom-format whole-database schema-and-data archive including Auth data, application/private schemas, Storage metadata and migration history.
4. Role definitions without login passwords, archive TOC, trusted CA copy and SHA-256 manifest.
5. Checksum verification before restore.
6. Restore into a network-isolated PostgreSQL 17 Supabase-compatible container.
7. Matching tables, columns, constraints, indexes, functions, triggers, enums, RLS, grants, migration history and representative record counts.
8. Documented unresolved coverage for Storage object bytes, platform Auth settings, Functions, secrets, Realtime, Firebase, web hosting and monitoring.

Schema-only output, a readable archive TOC, local migration replay or synthetic restore tests do not pass this gate. Hosted migrations remain disabled.
