-- Historical agreements have no reliable accepted version; retain NULL, never use today's settings.
ALTER TABLE gym_subscription
 ADD COLUMN agreement_version varchar(64) DEFAULT NULL,
 ADD KEY idx_subscription_member_store (tenant_id,member_id,deleted,store_id,id);
