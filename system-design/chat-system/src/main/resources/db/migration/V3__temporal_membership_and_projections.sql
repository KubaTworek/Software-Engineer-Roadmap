ALTER TABLE conversation_members DROP CONSTRAINT uk_conversation_member;
ALTER TABLE conversation_members ADD COLUMN joined_sequence BIGINT NOT NULL DEFAULT 1;
ALTER TABLE conversation_members ADD COLUMN left_sequence BIGINT;
ALTER TABLE conversation_members ADD COLUMN membership_epoch INTEGER NOT NULL DEFAULT 1;
ALTER TABLE conversation_members ADD COLUMN left_at TIMESTAMP;
ALTER TABLE conversation_members ADD CONSTRAINT uk_conversation_member_epoch
    UNIQUE (conversation_id, user_id, membership_epoch);
CREATE INDEX idx_conversation_members_active
    ON conversation_members(conversation_id, user_id, left_at);

CREATE TABLE websocket_tickets (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    username VARCHAR(80) NOT NULL,
    origin VARCHAR(300),
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    CONSTRAINT fk_websocket_ticket_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_websocket_ticket_expiry ON websocket_tickets(expires_at);

CREATE TABLE message_search_documents (
    message_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,
    body VARCHAR(4000),
    revision INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_search_message FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_search_conversation FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_search_sender FOREIGN KEY(sender_id) REFERENCES users(id)
);
CREATE INDEX idx_search_conversation_sequence
    ON message_search_documents(conversation_id, sequence_number);
