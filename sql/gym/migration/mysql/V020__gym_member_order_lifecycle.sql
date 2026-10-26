-- Existing orders keep unknown deadlines/actors; do not invent expiry or cancellation history.
ALTER TABLE gym_business_order
 ADD COLUMN buyer_user_id bigint DEFAULT NULL,
 ADD COLUMN expires_at datetime(6) DEFAULT NULL,
 ADD COLUMN cancelled_at datetime(6) DEFAULT NULL,
 ADD COLUMN cancellation_reason varchar(500) DEFAULT NULL,
 ADD KEY idx_order_member_channel (tenant_id,member_id,channel,deleted,id);
