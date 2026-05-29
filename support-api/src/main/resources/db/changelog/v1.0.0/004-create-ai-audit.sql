--liquibase formatted sql

--changeset krish:004-create-ai-audit
CREATE TABLE public.ai_response_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    model_name VARCHAR(100),
    latency_ms BIGINT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_response_audit_ticket_id FOREIGN KEY (ticket_id) REFERENCES public.tickets(id)
);
