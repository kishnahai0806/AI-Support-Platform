--liquibase formatted sql

--changeset krish:006-create-indexes
CREATE INDEX idx_tickets_customer_id ON public.tickets(customer_id);

CREATE INDEX idx_tickets_agent_id ON public.tickets(assigned_agent_id);

CREATE INDEX idx_tickets_status ON public.tickets(status);

CREATE INDEX idx_tickets_created_at ON public.tickets(created_at DESC);

CREATE INDEX idx_tickets_status_created ON public.tickets(status, created_at DESC);

CREATE INDEX idx_ticket_messages_ticket_id ON public.ticket_messages(ticket_id);

CREATE INDEX idx_ai_audit_ticket_id ON public.ai_response_audit(ticket_id);

CREATE INDEX idx_ai_audit_created_at ON public.ai_response_audit(created_at DESC);
