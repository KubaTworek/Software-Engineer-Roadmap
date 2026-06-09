CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(40) NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(120),
    created_by_id UUID,
    last_message_id UUID,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_conversations_created_by FOREIGN KEY (created_by_id) REFERENCES users(id)
);

CREATE TABLE conversation_members (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    last_read_at TIMESTAMP,
    CONSTRAINT uk_conversation_member UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_conversation_members_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_members_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_conversation_members_user ON conversation_members(user_id);
CREATE INDEX idx_conversation_members_conversation ON conversation_members(conversation_id);

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    upload_token VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    uploaded_at TIMESTAMP,
    CONSTRAINT uk_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_attachments_upload_token UNIQUE (upload_token),
    CONSTRAINT fk_attachments_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX idx_attachments_owner ON attachments(owner_id);
CREATE INDEX idx_attachments_upload_token ON attachments(upload_token);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    client_message_id UUID NOT NULL,
    body VARCHAR(4000),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sender_client_message UNIQUE (sender_id, client_message_id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender_client ON messages(sender_id, client_message_id);

ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_last_message FOREIGN KEY (last_message_id) REFERENCES messages(id);

CREATE TABLE message_attachments (
    message_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    CONSTRAINT pk_message_attachments PRIMARY KEY (message_id, attachment_id),
    CONSTRAINT fk_message_attachments_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_attachments_attachment FOREIGN KEY (attachment_id) REFERENCES attachments(id)
);

CREATE TABLE message_receipts (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    CONSTRAINT uk_message_recipient UNIQUE (message_id, recipient_id),
    CONSTRAINT fk_receipts_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_receipts_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_receipts_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
);

CREATE INDEX idx_receipts_user_conversation ON message_receipts(recipient_id, conversation_id);
CREATE INDEX idx_receipts_message ON message_receipts(message_id);
CREATE INDEX idx_receipts_unread ON message_receipts(conversation_id, recipient_id, read_at);

CREATE TABLE user_presence (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_presence_user UNIQUE (user_id),
    CONSTRAINT fk_user_presence_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE push_notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    message_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    CONSTRAINT fk_push_recipient FOREIGN KEY (recipient_id) REFERENCES users(id),
    CONSTRAINT fk_push_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_push_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);

CREATE INDEX idx_push_notifications_recipient_created ON push_notifications(recipient_id, created_at DESC);

CREATE TABLE blocked_users (
    id UUID PRIMARY KEY,
    blocker_id UUID NOT NULL,
    blocked_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_block_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT fk_blocked_users_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_blocked_users_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_blocked_users_blocker ON blocked_users(blocker_id);
CREATE INDEX idx_blocked_users_blocked ON blocked_users(blocked_id);

CREATE TABLE message_reports (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason VARCHAR(120) NOT NULL,
    details VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_message_reports_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)
);

CREATE INDEX idx_message_reports_status ON message_reports(status);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL,
    last_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_type_created ON outbox_events(event_type, created_at);
