-- =============================================================================
-- HyperLocal schema reference (PostgreSQL 18 + PostGIS 3.6)
--
-- Generated from the live database after Hibernate (spring.jpa.hibernate.ddl-auto=update)
-- applied the JPA entities. delivery_agents and unified_orders are the original blueprint
-- tables, extended with sign-in / KYC / address columns. Everything else is new.
--
-- This file is documentation only; Spring does NOT run it. For production, switch
-- ddl-auto to "validate" and manage changes with Flyway/Liquibase migrations.
--
-- NOTE: Hibernate adds CHECK constraints for enum columns. When you add a value to a Java
-- enum, drop/recreate that constraint, otherwise inserts with the new value will fail.
-- =============================================================================

-- Name: agent_documents; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.agent_documents (
    document_id uuid NOT NULL,
    agent_id uuid NOT NULL,
    document_number character varying(30),
    file_url character varying(500) NOT NULL,
    remarks character varying(250),
    status character varying(20) NOT NULL,
    doc_type character varying(20) NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT agent_documents_doc_type_check CHECK (((doc_type)::text = ANY ((ARRAY['AADHAAR'::character varying, 'PAN'::character varying, 'DRIVING_LICENSE'::character varying, 'VEHICLE_RC'::character varying, 'PROFILE_PHOTO'::character varying, 'BANK_PROOF'::character varying])::text[]))),
    CONSTRAINT agent_documents_status_check CHECK (((status)::text = ANY ((ARRAY['UPLOADED'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[])))
);

-- Name: customer_addresses; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.customer_addresses (
    address_id uuid NOT NULL,
    area character varying(100),
    building_name character varying(100),
    city character varying(60) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    custom_label character varying(30),
    customer_id uuid NOT NULL,
    is_default boolean NOT NULL,
    deleted boolean NOT NULL,
    delivery_instructions character varying(250),
    h3_index character varying(15),
    house_number character varying(50) NOT NULL,
    label character varying(10) NOT NULL,
    landmark character varying(150),
    location public.geometry(Point,4326) NOT NULL,
    pincode character varying(6) NOT NULL,
    recipient_name character varying(100),
    recipient_phone character varying(15),
    state character varying(60) NOT NULL,
    street character varying(150),
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT customer_addresses_label_check CHECK (((label)::text = ANY ((ARRAY['HOME'::character varying, 'WORK'::character varying, 'OTHER'::character varying])::text[])))
);

-- Name: customers; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.customers (
    customer_id uuid NOT NULL,
    account_status character varying(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(150),
    full_name character varying(100) NOT NULL,
    last_login_at timestamp(6) with time zone,
    password_hash character varying(100) NOT NULL,
    phone character varying(15) NOT NULL,
    phone_verified boolean NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT customers_account_status_check CHECK (((account_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'BLOCKED'::character varying, 'DELETED'::character varying])::text[])))
);

-- Name: delivery_agents; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.delivery_agents (
    agent_id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100),
    current_location public.geometry(Point,4326),
    h3_index character varying(15),
    status character varying(20) DEFAULT 'AVAILABLE'::character varying,
    updated_at timestamp with time zone DEFAULT now(),
    account_status character varying(20),
    city character varying(60),
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(150),
    last_login_at timestamp(6) with time zone,
    password_hash character varying(100),
    phone character varying(15),
    phone_verified boolean,
    rejection_reason character varying(250),
    thermal_box_id character varying(40),
    vehicle_number character varying(20),
    vehicle_type character varying(20),
    verification_status character varying(20),
    CONSTRAINT delivery_agents_account_status_check CHECK (((account_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'BLOCKED'::character varying, 'DELETED'::character varying])::text[]))),
    CONSTRAINT delivery_agents_vehicle_type_check CHECK (((vehicle_type)::text = ANY ((ARRAY['MOTORCYCLE'::character varying, 'SCOOTER'::character varying, 'EV_SCOOTER'::character varying, 'BICYCLE'::character varying])::text[]))),
    CONSTRAINT delivery_agents_verification_status_check CHECK (((verification_status)::text = ANY ((ARRAY['PENDING_DOCUMENTS'::character varying, 'UNDER_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

-- Name: otp_codes; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.otp_codes (
    otp_id uuid NOT NULL,
    attempts integer NOT NULL,
    code_hash character varying(64) NOT NULL,
    consumed boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    phone character varying(15) NOT NULL,
    purpose character varying(20) NOT NULL,
    role character varying(20) NOT NULL,
    CONSTRAINT otp_codes_purpose_check CHECK (((purpose)::text = ANY ((ARRAY['SIGNUP_VERIFY'::character varying, 'LOGIN'::character varying, 'PASSWORD_RESET'::character varying])::text[]))),
    CONSTRAINT otp_codes_role_check CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'DELIVERY_AGENT'::character varying, 'MERCHANT'::character varying, 'ADMIN'::character varying])::text[])))
);

-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.refresh_tokens (
    token_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone,
    role character varying(20) NOT NULL,
    token_hash character varying(64) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT refresh_tokens_role_check CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'DELIVERY_AGENT'::character varying, 'MERCHANT'::character varying, 'ADMIN'::character varying])::text[])))
);

-- Name: unified_orders; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.unified_orders (
    order_id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    restaurant_id uuid,
    kirana_id uuid,
    delivery_agent_id uuid,
    status character varying(30) DEFAULT 'PENDING'::character varying,
    is_batched boolean DEFAULT false,
    parent_batch_id uuid,
    financial_total numeric(10,2),
    created_at timestamp with time zone DEFAULT now(),
    delivery_address_id uuid
);

-- Name: agent_documents agent_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.agent_documents
    ADD CONSTRAINT agent_documents_pkey PRIMARY KEY (document_id);

-- Name: customer_addresses customer_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT customer_addresses_pkey PRIMARY KEY (address_id);

-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);

-- Name: delivery_agents delivery_agents_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.delivery_agents
    ADD CONSTRAINT delivery_agents_pkey PRIMARY KEY (agent_id);

-- Name: otp_codes otp_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.otp_codes
    ADD CONSTRAINT otp_codes_pkey PRIMARY KEY (otp_id);

-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (token_id);

-- Name: customers ukm3iom37efaxd5eucmxjqqcbe9; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT ukm3iom37efaxd5eucmxjqqcbe9 UNIQUE (phone);

-- Name: delivery_agents ukn7vgx6q2sbotwhb1psdlyx32h; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.delivery_agents
    ADD CONSTRAINT ukn7vgx6q2sbotwhb1psdlyx32h UNIQUE (phone);

-- Name: refresh_tokens uko2mlirhldriil2y7krapq4frt; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uko2mlirhldriil2y7krapq4frt UNIQUE (token_hash);

-- Name: customers ukrfbvkrffamfql7cjmen8v976v; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT ukrfbvkrffamfql7cjmen8v976v UNIQUE (email);

-- Name: delivery_agents ukrrfn4t0h97u3aip5fl0h2dr5; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.delivery_agents
    ADD CONSTRAINT ukrrfn4t0h97u3aip5fl0h2dr5 UNIQUE (email);

-- Name: unified_orders unified_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.unified_orders
    ADD CONSTRAINT unified_orders_pkey PRIMARY KEY (order_id);

-- Name: agent_documents uq_agent_document_type; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.agent_documents
    ADD CONSTRAINT uq_agent_document_type UNIQUE (agent_id, doc_type);

-- Name: idx_customer_addresses_customer; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_customer_addresses_customer ON public.customer_addresses USING btree (customer_id);

-- Name: idx_customer_addresses_h3; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_customer_addresses_h3 ON public.customer_addresses USING btree (h3_index);

-- Name: idx_delivery_agents_h3_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_delivery_agents_h3_status ON public.delivery_agents USING btree (h3_index, status);

-- Name: idx_otp_lookup; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_otp_lookup ON public.otp_codes USING btree (phone, role, purpose, created_at);

-- Name: idx_refresh_tokens_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_refresh_tokens_user ON public.refresh_tokens USING btree (user_id, role);

-- Name: unified_orders unified_orders_delivery_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.unified_orders
    ADD CONSTRAINT unified_orders_delivery_agent_id_fkey FOREIGN KEY (delivery_agent_id) REFERENCES public.delivery_agents(agent_id);
