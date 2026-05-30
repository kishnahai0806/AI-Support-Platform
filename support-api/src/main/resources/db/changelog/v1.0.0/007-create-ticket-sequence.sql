--liquibase formatted sql

--changeset krish:007-create-ticket-sequence
CREATE SEQUENCE IF NOT EXISTS public.ticket_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
