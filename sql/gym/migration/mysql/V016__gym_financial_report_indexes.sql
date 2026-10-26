-- Date-scoped sales totals and original payment timestamps; no ledger backfill or mutation.
CREATE INDEX idx_financial_store_time ON gym_financial_event (tenant_id,store_id,deleted,occurred_at);
CREATE INDEX idx_financial_order_payment ON gym_financial_event (tenant_id,business_order_id,store_id,product_type,employee_id,event_type,deleted,occurred_at);
