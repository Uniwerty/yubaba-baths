CREATE TABLE allocation_lock
(
    id bigint PRIMARY KEY
);

INSERT INTO allocation_lock (id) VALUES (1);

CREATE TABLE accounts
(
    id         bigserial PRIMARY KEY,
    version    bigint       NOT NULL DEFAULT 0,
    login      varchar(255) NOT NULL UNIQUE,
    password   varchar(255) NOT NULL,
    name       varchar(255) NOT NULL,
    role       varchar(255) NOT NULL,
    blocked    boolean      NOT NULL DEFAULT false,
    rest_until timestamptz
);

CREATE TABLE clients
(
    id      bigserial PRIMARY KEY,
    version bigint       NOT NULL DEFAULT 0,
    name    varchar(255) NOT NULL,
    contact varchar(255) NOT NULL UNIQUE,
    notes   varchar(255) NOT NULL
);

CREATE TABLE rooms
(
    id        bigserial PRIMARY KEY,
    version   bigint       NOT NULL DEFAULT 0,
    name      varchar(255) NOT NULL UNIQUE,
    bath_type varchar(255) NOT NULL,
    capacity  integer      NOT NULL
);

CREATE TABLE ingredients
(
    id        bigserial PRIMARY KEY,
    version   bigint         NOT NULL DEFAULT 0,
    name      varchar(255)   NOT NULL UNIQUE,
    unit      varchar(255)   NOT NULL,
    stock     numeric(14, 3) NOT NULL,
    reserved  numeric(14, 3) NOT NULL DEFAULT 0,
    threshold numeric(14, 3) NOT NULL,
    price     numeric(14, 2) NOT NULL
);

CREATE TABLE service_templates
(
    id                  bigserial PRIMARY KEY,
    version             bigint         NOT NULL DEFAULT 0,
    name                varchar(255)   NOT NULL,
    bath_type           varchar(255)   NOT NULL,
    preferred_room_id   bigint REFERENCES rooms,
    attendants          integer        NOT NULL,
    duration_minutes    integer        NOT NULL,
    preparation_minutes integer        NOT NULL,
    break_minutes       integer        NOT NULL,
    temperature         integer        NOT NULL,
    steps               varchar(4000)  NOT NULL,
    extra_services      varchar(2000)  NOT NULL,
    base_price          numeric(14, 2) NOT NULL
);

CREATE TABLE template_lines
(
    template_id   bigint         NOT NULL REFERENCES service_templates,
    line_no       integer        NOT NULL,
    ingredient_id bigint         NOT NULL REFERENCES ingredients,
    name          varchar(255)   NOT NULL,
    unit          varchar(255)   NOT NULL,
    quantity      numeric(14, 3) NOT NULL,
    unit_price    numeric(14, 2) NOT NULL,
    PRIMARY KEY (template_id, line_no)
);

CREATE TABLE bath_orders
(
    id                  bigserial PRIMARY KEY,
    version             bigint         NOT NULL DEFAULT 0,
    client_id           bigint         NOT NULL REFERENCES clients,
    template_id         bigint REFERENCES service_templates,
    service_name        varchar(255)   NOT NULL,
    bath_type           varchar(255)   NOT NULL,
    preferred_room_id   bigint REFERENCES rooms,
    room_id             bigint REFERENCES rooms,
    attendants          integer        NOT NULL,
    visitors            integer        NOT NULL,
    duration_minutes    integer        NOT NULL,
    preparation_minutes integer        NOT NULL,
    break_minutes       integer        NOT NULL,
    temperature         integer        NOT NULL,
    priority            integer        NOT NULL,
    steps               varchar(4000)  NOT NULL,
    extra_services      varchar(2000)  NOT NULL,
    base_price          numeric(14, 2) NOT NULL,
    total               numeric(14, 2) NOT NULL,
    status              varchar(255)   NOT NULL,
    created_at          timestamptz    NOT NULL,
    launched_at         timestamptz,
    water_ready_at      timestamptz,
    service_started_at  timestamptz,
    completed_at        timestamptz,
    closed_at           timestamptz,
    cancellation_reason varchar(1000)
);

CREATE UNIQUE INDEX one_active_order_per_room ON bath_orders (room_id)
    WHERE status IN ('IN_SERVICE','AWAITING_PAYMENT');

CREATE INDEX orders_status_created ON bath_orders (status, created_at);

CREATE TABLE order_lines
(
    order_id      bigint         NOT NULL REFERENCES bath_orders,
    line_no       integer        NOT NULL,
    ingredient_id bigint         NOT NULL REFERENCES ingredients,
    name          varchar(255)   NOT NULL,
    unit          varchar(255)   NOT NULL,
    quantity      numeric(14, 3) NOT NULL,
    unit_price    numeric(14, 2) NOT NULL,
    PRIMARY KEY (order_id, line_no)
);

CREATE TABLE order_attendants
(
    order_id   bigint NOT NULL REFERENCES bath_orders,
    account_id bigint NOT NULL REFERENCES accounts,
    PRIMARY KEY (order_id, account_id)
);

CREATE INDEX attendants_account ON order_attendants (account_id);

CREATE TABLE payments
(
    id          bigserial PRIMARY KEY,
    version     bigint         NOT NULL DEFAULT 0,
    order_id    bigint         NOT NULL UNIQUE REFERENCES bath_orders,
    amount      numeric(14, 2) NOT NULL,
    method      varchar(255)   NOT NULL,
    paid_at     timestamptz    NOT NULL,
    recorded_by bigint         NOT NULL REFERENCES accounts
);

CREATE INDEX payments_paid_at ON payments (paid_at);

CREATE TABLE audit_events
(
    id          bigserial PRIMARY KEY,
    version     bigint        NOT NULL DEFAULT 0,
    order_id    bigint        NOT NULL REFERENCES bath_orders,
    actor_id    bigint        NOT NULL REFERENCES accounts,
    action      varchar(255)  NOT NULL,
    details     varchar(2000) NOT NULL,
    occurred_at timestamptz   NOT NULL
);

CREATE INDEX audit_order ON audit_events (order_id, occurred_at);

CREATE TABLE report_templates
(
    id         bigserial PRIMARY KEY,
    version    bigint        NOT NULL DEFAULT 0,
    owner_id   bigint        NOT NULL REFERENCES accounts,
    name       varchar(255)  NOT NULL,
    parameters varchar(2000) NOT NULL
);

CREATE TABLE supply_requests
(
    id              bigserial PRIMARY KEY,
    version         bigint         NOT NULL DEFAULT 0,
    ingredient_id   bigint         NOT NULL REFERENCES ingredients,
    ingredient_name varchar(255)   NOT NULL,
    unit            varchar(255)   NOT NULL,
    quantity        numeric(14, 3) NOT NULL,
    status          varchar(255)   NOT NULL,
    created_at      timestamptz    NOT NULL,
    received_at     timestamptz
);

CREATE UNIQUE INDEX one_open_supply_per_ingredient ON supply_requests (ingredient_id)
    WHERE status='OPEN';
