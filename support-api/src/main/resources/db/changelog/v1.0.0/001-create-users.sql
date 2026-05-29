--liquibase formatted sql

--changeset krish:001-create-users
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE public.user_role AS ENUM (
    'ADMIN',
    'AGENT',
    'CUSTOMER'
);

CREATE TABLE public.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role public.user_role NOT NULL DEFAULT 'CUSTOMER'::public.user_role,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
