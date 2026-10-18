ALTER TABLE notification_campaigns ADD COLUMN claim_token UUID;
ALTER TABLE notification_campaigns ADD COLUMN claim_until TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_campaign_claim
    ON notification_campaigns(tenant_id, campaign_id, claim_until);
