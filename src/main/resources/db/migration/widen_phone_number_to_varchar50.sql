-- ==============================================================================
-- Database Migration: Widen users.phone_number to VARCHAR(50)
-- Target: PostgreSQL
-- Purpose:
--   Supports pseudonymized account-deletion markers (e.g. DELETED_<id>_<timestamp>)
--   while allowing former customers to re-register with their original 10-digit phone
--   number as a genuinely fresh account.
-- Safety & Compatibility:
--   1. Additive change: Widening VARCHAR(10) to VARCHAR(50) is an in-place metadata
--      operation in PostgreSQL (no full table rewrite, sub-millisecond execution).
--   2. Preserves all existing 10-digit phone numbers and user data.
--   3. Preserves the UNIQUE constraint and index on users.phone_number.
--   4. Preserves all foreign key relationships (orders.user_id, reviews.user_id).
--   5. Idempotent: Executing multiple times is safe and non-destructive.
-- ==============================================================================

ALTER TABLE users ALTER COLUMN phone_number TYPE VARCHAR(50);
