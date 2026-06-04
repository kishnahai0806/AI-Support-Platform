--liquibase formatted sql

--changeset krish:008-create-refresh-token-hash-index
-- Index on refresh_tokens(token_hash) is intentionally omitted.
-- The UNIQUE constraint defined in 005-create-refresh-tokens.sql
-- already creates an implicit B-tree index on this column in
-- PostgreSQL. A separate explicit index would be redundant.
