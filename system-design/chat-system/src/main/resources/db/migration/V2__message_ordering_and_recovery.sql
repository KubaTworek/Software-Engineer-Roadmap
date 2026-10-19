ALTER TABLE conversations ADD COLUMN direct_key VARCHAR(73);
ALTER TABLE conversations ADD COLUMN next_sequence BIGINT NOT NULL DEFAULT 1;
ALTER TABLE conversations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD CONSTRAINT uk_conversations_direct_key UNIQUE (direct_key);

ALTER TABLE messages DROP CONSTRAINT uk_sender_client_message;
ALTER TABLE messages ADD COLUMN sequence_number BIGINT NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN command_fingerprint VARCHAR(64) NOT NULL DEFAULT 'legacy';
ALTER TABLE messages ADD COLUMN revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE messages ADD COLUMN edited_at TIMESTAMP;
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE messages ADD CONSTRAINT uk_conversation_sender_client_message
    UNIQUE (conversation_id, sender_id, client_message_id);
ALTER TABLE messages ADD CONSTRAINT uk_conversation_sequence
    UNIQUE (conversation_id, sequence_number);
CREATE INDEX idx_messages_conversation_sequence ON messages(conversation_id, sequence_number DESC);

ALTER TABLE outbox_events ADD COLUMN claimed_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN claimed_by VARCHAR(120);
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_outbox_claimable ON outbox_events(status, next_attempt_at, created_at);

CREATE TABLE outbox_handler_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    handler_name VARCHAR(240) NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_outbox_handler_delivery UNIQUE(event_id, handler_name),
    CONSTRAINT fk_outbox_handler_event FOREIGN KEY(event_id) REFERENCES outbox_events(id) ON DELETE CASCADE
);

CREATE TABLE presence_sessions (
    session_id VARCHAR(160) PRIMARY KEY,
    user_id UUID NOT NULL,
    instance_id VARCHAR(120) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_presence_session_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_presence_user_expiry ON presence_sessions(user_id, expires_at);

ALTER TABLE attachments ALTER COLUMN upload_token DROP NOT NULL;
ALTER TABLE attachments ADD COLUMN upload_expires_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE attachments ADD COLUMN checksum VARCHAR(64);

CREATE TABLE device_sync_cursors (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id VARCHAR(120) NOT NULL,
    conversation_id UUID NOT NULL,
    last_sequence BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_device_conversation_cursor UNIQUE(user_id, device_id, conversation_id),
    CONSTRAINT fk_device_cursor_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_device_cursor_conversation FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);
