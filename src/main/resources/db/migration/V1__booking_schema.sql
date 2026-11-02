CREATE TABLE event_inventory (
    event_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    total_tickets INTEGER NOT NULL,
    available_tickets INTEGER NOT NULL,
    sale_opens_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sale_closes_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ticket_price NUMERIC(10, 2) NOT NULL
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event_inventory (event_id),
    customer_email VARCHAR(254) NOT NULL,
    quantity INTEGER NOT NULL,
    total_price NUMERIC(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_reservations_expire ON reservations (status, expires_at);

CREATE TABLE reservation_idempotency_keys (
    event_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    reservation_id UUID NOT NULL REFERENCES reservations (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id, idempotency_key)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    partition_key UUID NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at);
