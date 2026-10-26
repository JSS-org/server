-- Unknown legacy lesson lengths stay NULL; existing contract events were backend actors.
ALTER TABLE gym_course_pack ADD COLUMN duration_minutes_snapshot int DEFAULT NULL;
ALTER TABLE gym_contract_event ADD COLUMN actor_user_type int NOT NULL DEFAULT 2;

-- Recover only persisted member purchases; do not invent identities or payment receipts.
-- Stop/upgrade all old fulfillment workers before enabling member tasks.
INSERT INTO gym_fulfillment_task
 (order_id, principal_user_id, principal_user_type, status, attempt_count, attempt_limit, next_attempt_at, tenant_id)
SELECT o.id, o.buyer_user_id, 1,
 IF(o.paid_amount=o.payable_amount, 'pending', 'waiting_payment'), 0, 8,
 IF(o.paid_amount=o.payable_amount, CURRENT_TIMESTAMP(6), NULL), o.tenant_id
FROM gym_business_order o
JOIN gym_order_policy p ON p.order_id=o.id AND p.tenant_id=o.tenant_id AND p.deleted=0
WHERE o.deleted=0 AND o.buyer_user_id IS NOT NULL AND o.channel='miniapp'
 AND o.business_type IN ('card_sale','course_sale')
 AND o.status IN ('pending_payment','partially_paid','paid','pending_fulfillment')
 AND o.refunded_amount=0 AND o.payable_amount>=0 AND o.paid_amount>=0 AND o.paid_amount<=o.payable_amount
 AND p.approval_required=0 AND p.approved_approval_id IS NULL AND p.fulfilled_at IS NULL
 AND NOT EXISTS (SELECT 1 FROM gym_fulfillment_task t WHERE t.order_id=o.id AND t.tenant_id=o.tenant_id)
 AND (SELECT COUNT(*) FROM gym_order_item i WHERE i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0)=1
 AND EXISTS (SELECT 1 FROM gym_order_item i WHERE i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0
  AND i.quantity=1 AND CONCAT(i.product_type,'_sale')=o.business_type
  AND i.unit_price=o.payable_amount AND i.payable_amount=o.payable_amount);
