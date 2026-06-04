--liquibase formatted sql

--changeset krish:008-create-refresh-token-hash-index
CREATE INDEX idx_refresh_tokens_token_hash ON public.refresh_tokens(token_hash);
