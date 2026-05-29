--liquibase formatted sql

--changeset krish:003-create-messages
CREATE TABLE public.ticket_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content TEXT NOT NULL,
    is_ai_generated BOOLEAN NOT NULL DEFAULT false,
    ai_model_used VARCHAR(100),
    ai_tokens_used INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_messages_ticket_id FOREIGN KEY (ticket_id) REFERENCES public.tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_messages_sender_id FOREIGN KEY (sender_id) REFERENCES public.users(id)
);
