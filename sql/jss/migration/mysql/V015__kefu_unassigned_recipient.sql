-- The original member send flow broadcasts to the tenant's customer-service team.
-- No individual administrator is assigned at this point; the authenticated sender and
-- conversation remain mandatory. Admin replies still set their member recipient.
ALTER TABLE promotion_kefu_message
    MODIFY receiver_id BIGINT NULL,
    MODIFY receiver_type INT NULL;
