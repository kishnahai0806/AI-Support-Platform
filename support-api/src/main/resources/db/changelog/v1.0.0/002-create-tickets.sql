--liquibase formatted sql

--changeset krish:002-create-tickets
CREATE TYPE public.ticket_status AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'AI_PROCESSING',
    'WAITING_CUSTOMER',
    'RESOLVED',
    'CLOSED',
    'ESCALATED'
);

CREATE TYPE public.ticket_priority AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL'
);

CREATE TYPE public.ticket_category AS ENUM (
    'BILLING',
    'TECHNICAL',
    'ACCOUNT',
    'SHIPPING',
    'GENERAL',
    'COMPLAINT',
    'FEATURE_REQUEST'
);

CREATE TABLE public.tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    status public.ticket_status NOT NULL DEFAULT 'OPEN'::public.ticket_status,
    priority public.ticket_priority NOT NULL DEFAULT 'MEDIUM'::public.ticket_priority,
    category public.ticket_category,
    customer_id UUID NOT NULL,
    assigned_agent_id UUID,
    ai_confidence_score DECIMAL(5,4),
    ai_suggested_category public.ticket_category,
    ai_processed_at TIMESTAMP,
    ai_escalated BOOLEAN DEFAULT false,
    first_response_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tickets_customer_id FOREIGN KEY (customer_id) REFERENCES public.users(id),
    CONSTRAINT fk_tickets_assigned_agent_id FOREIGN KEY (assigned_agent_id) REFERENCES public.users(id)
);
