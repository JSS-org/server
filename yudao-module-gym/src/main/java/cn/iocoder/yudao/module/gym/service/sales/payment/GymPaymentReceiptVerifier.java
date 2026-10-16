package cn.iocoder.yudao.module.gym.service.sales.payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Server-side provider adapter. Return only terminal successful transactions authenticated with
 * trusted merchant credentials. Resolve merchant ownership to tenantId, check order association,
 * currency/amount and provider transaction identity. Pending/failed/unknown receipts must throw.
 * The reference is only a lookup key; caller-submitted success fields are never evidence.
 */
public interface GymPaymentReceiptVerifier {
    Receipt payment(String receiptReference);

    Receipt refund(String receiptReference);

    record Receipt(
            Long tenantId,
            String provider,
            String receiptId,
            String orderNo,
            String paymentReceiptId,
            String currency,
            BigDecimal amount,
            Instant occurredAt) {}
}
