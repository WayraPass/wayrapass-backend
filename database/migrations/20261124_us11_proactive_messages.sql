CREATE TABLE IF NOT EXISTS wayria_proactive_messages (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    trip_id BIGINT NOT NULL REFERENCES trips(id),
    incident_id BIGINT REFERENCES incidents(id),
    type VARCHAR(30) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wayria_proactive_trip_recipient_type UNIQUE (trip_id, recipient_user_id, type)
);
CREATE INDEX IF NOT EXISTS idx_wayria_proactive_recipient_created ON wayria_proactive_messages(recipient_user_id, created_at);
