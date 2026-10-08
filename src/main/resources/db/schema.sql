-- =============================================================================
-- HyperLocal schema reference (PostgreSQL 18 + PostGIS 3.6)
--
-- Generated with pg_dump --schema-only after the app (Hibernate ddl-auto=update + db/indexes.sql)
-- upgraded a copy of the original database. delivery_agents and unified_orders are the original
-- blueprint tables, extended with new columns; everything else is new.
--
-- This file is documentation only; Spring does NOT run it (it runs db/indexes.sql). For
-- production, switch ddl-auto to "validate" and manage changes with Flyway/Liquibase migrations.
--
-- NOTE: Hibernate adds CHECK constraints for enum columns. When you add a value to a Java
-- enum, drop/recreate that constraint, otherwise inserts with the new value will fail.
-- =============================================================================

\restrict ML0Ok2DddQ7EmYdNWFwY8WeMTdO1uI3W4sOCLzAIbtzEfPmvpiqwjThetBHAbx6

-- Name: postgis; Type: EXTENSION; Schema: -; Owner: -

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;

-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner: -

COMMENT ON EXTENSION postgis IS 'PostGIS geometry and geography spatial types and functions';

-- Name: admin_users; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.admin_users (
    admin_id uuid NOT NULL,
    account_status character varying(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(150) NOT NULL,
    full_name character varying(100) NOT NULL,
    last_login_at timestamp(6) with time zone,
    password_hash character varying(100) NOT NULL,
    phone character varying(15),
    phone_verified boolean NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT admin_users_account_status_check CHECK (((account_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'BLOCKED'::character varying, 'DELETED'::character varying])::text[])))
);

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
    CONSTRAINT agent_documents_doc_type_check CHECK (((doc_type)::text = ANY (ARRAY[('AADHAAR'::character varying)::text, ('PAN'::character varying)::text, ('DRIVING_LICENSE'::character varying)::text, ('VEHICLE_RC'::character varying)::text, ('PROFILE_PHOTO'::character varying)::text, ('BANK_PROOF'::character varying)::text]))),
    CONSTRAINT agent_documents_status_check CHECK (((status)::text = ANY (ARRAY[('UPLOADED'::character varying)::text, ('VERIFIED'::character varying)::text, ('REJECTED'::character varying)::text])))
);

-- Name: agent_earnings; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.agent_earnings (
    earning_id uuid NOT NULL,
    agent_id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    order_id uuid NOT NULL,
    earning_type character varying(20) NOT NULL,
    CONSTRAINT agent_earnings_earning_type_check CHECK (((earning_type)::text = ANY ((ARRAY['BASE'::character varying, 'MULTI_STOP_BONUS'::character varying, 'BATCH_INCENTIVE'::character varying])::text[])))
);

-- Name: agent_payouts; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.agent_payouts (
    payout_id uuid NOT NULL,
    agent_id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    paid_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    utr character varying(40),
    CONSTRAINT agent_payouts_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'FAILED'::character varying])::text[])))
);

-- Name: broadcast_offers; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.broadcast_offers (
    offer_id uuid NOT NULL,
    claimed_at timestamp(6) with time zone,
    claimed_by uuid,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone,
    order_id uuid NOT NULL,
    origin_lat double precision NOT NULL,
    origin_lng double precision NOT NULL,
    round integer NOT NULL,
    starts_at timestamp(6) with time zone NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT broadcast_offers_status_check CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'OPEN'::character varying, 'CLAIMED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);

-- Name: broadcast_targets; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.broadcast_targets (
    target_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    distance_meters integer NOT NULL,
    merchant_id uuid NOT NULL,
    offer_id uuid NOT NULL
);

-- Name: cart_items; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.cart_items (
    cart_item_id uuid NOT NULL,
    cart_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    item_id uuid NOT NULL,
    quantity integer NOT NULL,
    item_type character varying(10) NOT NULL,
    CONSTRAINT cart_items_item_type_check CHECK (((item_type)::text = ANY ((ARRAY['FOOD'::character varying, 'GROCERY'::character varying])::text[])))
);

-- Name: carts; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.carts (
    cart_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    customer_id uuid NOT NULL,
    delivery_address_id uuid,
    restaurant_id uuid,
    updated_at timestamp(6) with time zone NOT NULL
);

-- Name: catalog_items; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.catalog_items (
    item_id uuid NOT NULL,
    available boolean NOT NULL,
    brand character varying(60),
    category character varying(60) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(500),
    image_url character varying(500),
    merchant_id uuid NOT NULL,
    mrp numeric(10,2),
    name character varying(120) NOT NULL,
    pack_size character varying(30),
    price numeric(10,2) NOT NULL,
    item_type character varying(10) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    veg boolean,
    zone character varying(10) NOT NULL,
    CONSTRAINT catalog_items_item_type_check CHECK (((item_type)::text = ANY ((ARRAY['FOOD'::character varying, 'GROCERY'::character varying])::text[]))),
    CONSTRAINT catalog_items_zone_check CHECK (((zone)::text = ANY ((ARRAY['HOT'::character varying, 'COLD'::character varying, 'AMBIENT'::character varying])::text[])))
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
    CONSTRAINT customer_addresses_label_check CHECK (((label)::text = ANY (ARRAY[('HOME'::character varying)::text, ('WORK'::character varying)::text, ('OTHER'::character varying)::text])))
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
    CONSTRAINT customers_account_status_check CHECK (((account_status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('BLOCKED'::character varying)::text, ('DELETED'::character varying)::text])))
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
    CONSTRAINT delivery_agents_account_status_check CHECK (((account_status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('BLOCKED'::character varying)::text, ('DELETED'::character varying)::text]))),
    CONSTRAINT delivery_agents_vehicle_type_check CHECK (((vehicle_type)::text = ANY (ARRAY[('MOTORCYCLE'::character varying)::text, ('SCOOTER'::character varying)::text, ('EV_SCOOTER'::character varying)::text, ('BICYCLE'::character varying)::text]))),
    CONSTRAINT delivery_agents_verification_status_check CHECK (((verification_status)::text = ANY (ARRAY[('PENDING_DOCUMENTS'::character varying)::text, ('UNDER_REVIEW'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text])))
);

-- Name: merchant_settlements; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.merchant_settlements (
    settlement_id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    merchant_id uuid NOT NULL,
    order_id uuid NOT NULL
);

-- Name: merchants; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.merchants (
    merchant_id uuid NOT NULL,
    account_status character varying(20) NOT NULL,
    address character varying(300) NOT NULL,
    approval_status character varying(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    cuisines character varying(200),
    fssai_number character varying(14),
    gstin character varying(15),
    h3_index character varying(15) NOT NULL,
    image_url character varying(500),
    last_login_at timestamp(6) with time zone,
    location public.geometry(Point,4326) NOT NULL,
    is_open boolean NOT NULL,
    owner_name character varying(100) NOT NULL,
    password_hash character varying(100) NOT NULL,
    phone character varying(15) NOT NULL,
    phone_verified boolean NOT NULL,
    pincode character varying(6) NOT NULL,
    rating_count integer NOT NULL,
    rating_sum integer NOT NULL,
    rejection_reason character varying(250),
    store_name character varying(120) NOT NULL,
    merchant_type character varying(12) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    upi_vpa character varying(100),
    CONSTRAINT merchants_account_status_check CHECK (((account_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'BLOCKED'::character varying, 'DELETED'::character varying])::text[]))),
    CONSTRAINT merchants_approval_status_check CHECK (((approval_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT merchants_merchant_type_check CHECK (((merchant_type)::text = ANY ((ARRAY['KIRANA'::character varying, 'RESTAURANT'::character varying])::text[])))
);

-- Name: order_items; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.order_items (
    order_item_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deficient boolean NOT NULL,
    item_id uuid NOT NULL,
    line_total numeric(10,2) NOT NULL,
    name character varying(120) NOT NULL,
    order_id uuid NOT NULL,
    pack_size character varying(30),
    quantity integer NOT NULL,
    item_type character varying(10) NOT NULL,
    unit_price numeric(10,2) NOT NULL,
    zone character varying(10) NOT NULL,
    CONSTRAINT order_items_item_type_check CHECK (((item_type)::text = ANY ((ARRAY['FOOD'::character varying, 'GROCERY'::character varying])::text[]))),
    CONSTRAINT order_items_zone_check CHECK (((zone)::text = ANY ((ARRAY['HOT'::character varying, 'COLD'::character varying, 'AMBIENT'::character varying])::text[])))
);

-- Name: order_ratings; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.order_ratings (
    order_id uuid NOT NULL,
    comment character varying(500),
    created_at timestamp(6) with time zone NOT NULL,
    customer_id uuid NOT NULL,
    delivery_rating integer,
    kirana_rating integer,
    restaurant_rating integer
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
    CONSTRAINT otp_codes_purpose_check CHECK (((purpose)::text = ANY (ARRAY[('SIGNUP_VERIFY'::character varying)::text, ('LOGIN'::character varying)::text, ('PASSWORD_RESET'::character varying)::text]))),
    CONSTRAINT otp_codes_role_check CHECK (((role)::text = ANY (ARRAY[('CUSTOMER'::character varying)::text, ('DELIVERY_AGENT'::character varying)::text, ('MERCHANT'::character varying)::text, ('ADMIN'::character varying)::text])))
);

-- Name: payment_webhook_events; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.payment_webhook_events (
    event_id character varying(150) NOT NULL,
    event_type character varying(30) NOT NULL,
    gateway_reference character varying(100),
    received_at timestamp(6) with time zone NOT NULL
);

-- Name: payments; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.payments (
    payment_id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    captured_amount numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    customer_id uuid NOT NULL,
    expires_at timestamp(6) with time zone,
    gateway character varying(20) NOT NULL,
    gateway_payment_id character varying(100),
    gateway_reference character varying(100),
    method character varying(10) NOT NULL,
    order_id uuid NOT NULL,
    refunded_amount numeric(10,2) NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    upi_intent_url character varying(500),
    version bigint NOT NULL,
    CONSTRAINT payments_method_check CHECK (((method)::text = ANY ((ARRAY['UPI'::character varying, 'WALLET'::character varying])::text[]))),
    CONSTRAINT payments_status_check CHECK (((status)::text = ANY ((ARRAY['HOLD_PENDING'::character varying, 'HELD'::character varying, 'CAPTURED'::character varying, 'RELEASED'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'REFUNDED'::character varying, 'FAILED'::character varying])::text[])))
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
    CONSTRAINT refresh_tokens_role_check CHECK (((role)::text = ANY (ARRAY[('CUSTOMER'::character varying)::text, ('DELIVERY_AGENT'::character varying)::text, ('MERCHANT'::character varying)::text, ('ADMIN'::character varying)::text])))
);

-- Name: rider_routes; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.rider_routes (
    route_id uuid NOT NULL,
    agent_id uuid NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT rider_routes_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying])::text[])))
);

-- Name: route_injections; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.route_injections (
    injection_id uuid NOT NULL,
    acknowledged_at timestamp(6) with time zone,
    added_delay_seconds bigint NOT NULL,
    agent_id uuid NOT NULL,
    bonus numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    injected_order_id uuid NOT NULL,
    primary_order_id uuid NOT NULL,
    route_id uuid NOT NULL
);

-- Name: route_stops; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.route_stops (
    stop_id uuid NOT NULL,
    address character varying(300),
    agent_id uuid NOT NULL,
    arrived_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    eta timestamp(6) with time zone,
    location public.geometry(Point,4326) NOT NULL,
    merchant_id uuid,
    name character varying(120) NOT NULL,
    order_id uuid NOT NULL,
    route_id uuid NOT NULL,
    sequence integer NOT NULL,
    state character varying(20) NOT NULL,
    stop_type character varying(20) NOT NULL,
    CONSTRAINT route_stops_state_check CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'EN_ROUTE'::character varying, 'ARRIVED'::character varying, 'COMPLETED'::character varying, 'SKIPPED'::character varying])::text[]))),
    CONSTRAINT route_stops_stop_type_check CHECK (((stop_type)::text = ANY ((ARRAY['KIRANA'::character varying, 'RESTAURANT'::character varying, 'DARK_STORE'::character varying, 'CUSTOMER'::character varying])::text[])))
);

-- Name: task_offers; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.task_offers (
    offer_id uuid NOT NULL,
    agent_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    distance_meters integer NOT NULL,
    estimated_earnings numeric(10,2) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    order_id uuid NOT NULL,
    reject_reason character varying(100),
    responded_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    CONSTRAINT task_offers_status_check CHECK (((status)::text = ANY ((ARRAY['OFFERED'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
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
    delivery_address_id uuid,
    cancel_reason character varying(250),
    cancelled_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    convenience_fee numeric(10,2),
    delivery_fee numeric(10,2),
    delivery_otp character varying(4),
    discount numeric(10,2),
    drop_h3 character varying(15),
    drop_location public.geometry(Point,4326),
    food_status character varying(20),
    food_subtotal numeric(10,2),
    grocery_status character varying(20),
    grocery_subtotal numeric(10,2),
    idempotency_key character varying(64),
    payment_method character varying(10),
    prep_ready_at timestamp(6) with time zone,
    taxes numeric(10,2),
    updated_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT unified_orders_food_status_check CHECK (((food_status)::text = ANY ((ARRAY['AWAITING_PAYMENT'::character varying, 'NEW'::character varying, 'BROADCASTING'::character varying, 'ACCEPTED'::character varying, 'DELAYED'::character varying, 'READY'::character varying, 'PICKED_UP'::character varying, 'UNFULFILLED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT unified_orders_grocery_status_check CHECK (((grocery_status)::text = ANY ((ARRAY['AWAITING_PAYMENT'::character varying, 'NEW'::character varying, 'BROADCASTING'::character varying, 'ACCEPTED'::character varying, 'DELAYED'::character varying, 'READY'::character varying, 'PICKED_UP'::character varying, 'UNFULFILLED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT unified_orders_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['UPI'::character varying, 'WALLET'::character varying])::text[])))
);

-- Name: wallet_entries; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.wallet_entries (
    entry_id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    customer_id uuid NOT NULL,
    order_id uuid,
    reason character varying(150) NOT NULL,
    reference character varying(120) NOT NULL,
    type character varying(10) NOT NULL,
    CONSTRAINT wallet_entries_type_check CHECK (((type)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[])))
);

-- Name: admin_users admin_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_pkey PRIMARY KEY (admin_id);

-- Name: agent_documents agent_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.agent_documents
    ADD CONSTRAINT agent_documents_pkey PRIMARY KEY (document_id);

-- Name: agent_earnings agent_earnings_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.agent_earnings
    ADD CONSTRAINT agent_earnings_pkey PRIMARY KEY (earning_id);

-- Name: agent_payouts agent_payouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.agent_payouts
    ADD CONSTRAINT agent_payouts_pkey PRIMARY KEY (payout_id);

-- Name: broadcast_offers broadcast_offers_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.broadcast_offers
    ADD CONSTRAINT broadcast_offers_pkey PRIMARY KEY (offer_id);

-- Name: broadcast_targets broadcast_targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.broadcast_targets
    ADD CONSTRAINT broadcast_targets_pkey PRIMARY KEY (target_id);

-- Name: cart_items cart_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_pkey PRIMARY KEY (cart_item_id);

-- Name: carts carts_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT carts_pkey PRIMARY KEY (cart_id);

-- Name: catalog_items catalog_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.catalog_items
    ADD CONSTRAINT catalog_items_pkey PRIMARY KEY (item_id);

-- Name: customer_addresses customer_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT customer_addresses_pkey PRIMARY KEY (address_id);

-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);

-- Name: delivery_agents delivery_agents_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.delivery_agents
    ADD CONSTRAINT delivery_agents_pkey PRIMARY KEY (agent_id);

-- Name: merchant_settlements merchant_settlements_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.merchant_settlements
    ADD CONSTRAINT merchant_settlements_pkey PRIMARY KEY (settlement_id);

-- Name: merchants merchants_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.merchants
    ADD CONSTRAINT merchants_pkey PRIMARY KEY (merchant_id);

-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (order_item_id);

-- Name: order_ratings order_ratings_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.order_ratings
    ADD CONSTRAINT order_ratings_pkey PRIMARY KEY (order_id);

-- Name: otp_codes otp_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.otp_codes
    ADD CONSTRAINT otp_codes_pkey PRIMARY KEY (otp_id);

-- Name: payment_webhook_events payment_webhook_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.payment_webhook_events
    ADD CONSTRAINT payment_webhook_events_pkey PRIMARY KEY (event_id);

-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (payment_id);

-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (token_id);

-- Name: rider_routes rider_routes_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.rider_routes
    ADD CONSTRAINT rider_routes_pkey PRIMARY KEY (route_id);

-- Name: route_injections route_injections_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.route_injections
    ADD CONSTRAINT route_injections_pkey PRIMARY KEY (injection_id);

-- Name: route_stops route_stops_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.route_stops
    ADD CONSTRAINT route_stops_pkey PRIMARY KEY (stop_id);

-- Name: task_offers task_offers_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.task_offers
    ADD CONSTRAINT task_offers_pkey PRIMARY KEY (offer_id);

-- Name: merchants uk2nbsqty98194vs6km7898da52; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.merchants
    ADD CONSTRAINT uk2nbsqty98194vs6km7898da52 UNIQUE (phone);

-- Name: wallet_entries uk6jaac3kbfjj9go8bjmifwhlui; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.wallet_entries
    ADD CONSTRAINT uk6jaac3kbfjj9go8bjmifwhlui UNIQUE (reference);

-- Name: carts uk88sv4i13lo80s74ox7rsb5a2c; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT uk88sv4i13lo80s74ox7rsb5a2c UNIQUE (customer_id);

-- Name: payments uk8vo36cen604as7etdfwmyjsxt; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT uk8vo36cen604as7etdfwmyjsxt UNIQUE (order_id);

-- Name: payments uk9v7e32gxe6xobw4amlnq8pr7k; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT uk9v7e32gxe6xobw4amlnq8pr7k UNIQUE (gateway_reference);

-- Name: admin_users ukcp8822350s9vtyww7xdbgeuvu; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT ukcp8822350s9vtyww7xdbgeuvu UNIQUE (email);

-- Name: customers ukm3iom37efaxd5eucmxjqqcbe9; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT ukm3iom37efaxd5eucmxjqqcbe9 UNIQUE (phone);

-- Name: delivery_agents ukn7vgx6q2sbotwhb1psdlyx32h; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.delivery_agents
    ADD CONSTRAINT ukn7vgx6q2sbotwhb1psdlyx32h UNIQUE (phone);

-- Name: refresh_tokens uko2mlirhldriil2y7krapq4frt; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uko2mlirhldriil2y7krapq4frt UNIQUE (token_hash);

-- Name: admin_users ukonxe2va3aiq94krm8ja7len0q; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT ukonxe2va3aiq94krm8ja7len0q UNIQUE (phone);

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

-- Name: agent_earnings uq_agent_earnings; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.agent_earnings
    ADD CONSTRAINT uq_agent_earnings UNIQUE (agent_id, order_id, earning_type);

-- Name: broadcast_targets uq_broadcast_targets; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.broadcast_targets
    ADD CONSTRAINT uq_broadcast_targets UNIQUE (offer_id, merchant_id);

-- Name: cart_items uq_cart_items_item; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT uq_cart_items_item UNIQUE (cart_id, item_id);

-- Name: merchant_settlements uq_merchant_settlements; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.merchant_settlements
    ADD CONSTRAINT uq_merchant_settlements UNIQUE (merchant_id, order_id);

-- Name: unified_orders uq_orders_idempotency; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.unified_orders
    ADD CONSTRAINT uq_orders_idempotency UNIQUE (customer_id, idempotency_key);

-- Name: wallet_entries wallet_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.wallet_entries
    ADD CONSTRAINT wallet_entries_pkey PRIMARY KEY (entry_id);

-- Name: idx_agent_earnings_agent_created; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_agent_earnings_agent_created ON public.agent_earnings USING btree (agent_id, created_at);

-- Name: idx_agent_payouts_agent; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_agent_payouts_agent ON public.agent_payouts USING btree (agent_id, created_at);

-- Name: idx_broadcast_offers_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_broadcast_offers_order ON public.broadcast_offers USING btree (order_id);

-- Name: idx_broadcast_offers_status_expires; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_broadcast_offers_status_expires ON public.broadcast_offers USING btree (status, expires_at);

-- Name: idx_broadcast_offers_status_starts; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_broadcast_offers_status_starts ON public.broadcast_offers USING btree (status, starts_at);

-- Name: idx_broadcast_targets_merchant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_broadcast_targets_merchant ON public.broadcast_targets USING btree (merchant_id);

-- Name: idx_cart_items_cart; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_cart_items_cart ON public.cart_items USING btree (cart_id);

-- Name: idx_catalog_items_category; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_catalog_items_category ON public.catalog_items USING btree (category);

-- Name: idx_catalog_items_merchant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_catalog_items_merchant ON public.catalog_items USING btree (merchant_id);

-- Name: idx_catalog_items_type_available; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_catalog_items_type_available ON public.catalog_items USING btree (item_type, available);

-- Name: idx_customer_addresses_customer; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_customer_addresses_customer ON public.customer_addresses USING btree (customer_id);

-- Name: idx_customer_addresses_h3; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_customer_addresses_h3 ON public.customer_addresses USING btree (h3_index);

-- Name: idx_customer_addresses_location_gist; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_customer_addresses_location_gist ON public.customer_addresses USING gist (location);

-- Name: idx_delivery_agents_h3_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_delivery_agents_h3_status ON public.delivery_agents USING btree (h3_index, status);

-- Name: idx_delivery_agents_location_gist; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_delivery_agents_location_gist ON public.delivery_agents USING gist (current_location);

-- Name: idx_merchant_settlements_merchant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_merchant_settlements_merchant ON public.merchant_settlements USING btree (merchant_id, created_at);

-- Name: idx_merchants_discovery; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_merchants_discovery ON public.merchants USING btree (merchant_type, approval_status, is_open);

-- Name: idx_merchants_h3; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_merchants_h3 ON public.merchants USING btree (h3_index);

-- Name: idx_merchants_location_gist; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_merchants_location_gist ON public.merchants USING gist (location);

-- Name: idx_order_items_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_order_items_order ON public.order_items USING btree (order_id);

-- Name: idx_order_ratings_customer; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_order_ratings_customer ON public.order_ratings USING btree (customer_id);

-- Name: idx_orders_agent_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_orders_agent_status ON public.unified_orders USING btree (delivery_agent_id, status);

-- Name: idx_orders_customer_created; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_orders_customer_created ON public.unified_orders USING btree (customer_id, created_at);

-- Name: idx_orders_kirana; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_orders_kirana ON public.unified_orders USING btree (kirana_id, grocery_status);

-- Name: idx_orders_parent_batch; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_orders_parent_batch ON public.unified_orders USING btree (parent_batch_id);

-- Name: idx_orders_restaurant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_orders_restaurant ON public.unified_orders USING btree (restaurant_id, food_status);

-- Name: idx_orders_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_orders_status ON public.unified_orders USING btree (status);

-- Name: idx_otp_lookup; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_otp_lookup ON public.otp_codes USING btree (phone, role, purpose, created_at);

-- Name: idx_payments_customer; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_payments_customer ON public.payments USING btree (customer_id);

-- Name: idx_refresh_tokens_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_refresh_tokens_user ON public.refresh_tokens USING btree (user_id, role);

-- Name: idx_rider_routes_agent_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_rider_routes_agent_status ON public.rider_routes USING btree (agent_id, status);

-- Name: idx_route_injections_agent; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_route_injections_agent ON public.route_injections USING btree (agent_id);

-- Name: idx_route_injections_route; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_route_injections_route ON public.route_injections USING btree (route_id);

-- Name: idx_route_stops_agent_state; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_route_stops_agent_state ON public.route_stops USING btree (agent_id, state);

-- Name: idx_route_stops_location_gist; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_route_stops_location_gist ON public.route_stops USING gist (location);

-- Name: idx_route_stops_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_route_stops_order ON public.route_stops USING btree (order_id);

-- Name: idx_route_stops_route_sequence; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_route_stops_route_sequence ON public.route_stops USING btree (route_id, sequence);

-- Name: idx_task_offers_agent_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_task_offers_agent_status ON public.task_offers USING btree (agent_id, status);

-- Name: idx_task_offers_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_task_offers_order ON public.task_offers USING btree (order_id);

-- Name: idx_task_offers_status_expires; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_task_offers_status_expires ON public.task_offers USING btree (status, expires_at);

-- Name: idx_unified_orders_drop_gist; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_unified_orders_drop_gist ON public.unified_orders USING gist (drop_location);

-- Name: idx_wallet_entries_customer; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_wallet_entries_customer ON public.wallet_entries USING btree (customer_id, created_at);

-- Name: idx_wallet_entries_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_wallet_entries_order ON public.wallet_entries USING btree (order_id);

-- Name: uq_rider_routes_active; Type: INDEX; Schema: public; Owner: -

CREATE UNIQUE INDEX uq_rider_routes_active ON public.rider_routes USING btree (agent_id) WHERE ((status)::text = 'ACTIVE'::text);

-- Name: uq_task_offers_open_per_order; Type: INDEX; Schema: public; Owner: -

CREATE UNIQUE INDEX uq_task_offers_open_per_order ON public.task_offers USING btree (order_id) WHERE ((status)::text = 'OFFERED'::text);

-- Name: unified_orders unified_orders_delivery_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.unified_orders
    ADD CONSTRAINT unified_orders_delivery_agent_id_fkey FOREIGN KEY (delivery_agent_id) REFERENCES public.delivery_agents(agent_id);

\unrestrict ML0Ok2DddQ7EmYdNWFwY8WeMTdO1uI3W4sOCLzAIbtzEfPmvpiqwjThetBHAbx6

