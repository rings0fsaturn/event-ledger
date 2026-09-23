package com.flashsale.ledger.payment;

import lombok.Builder;

@Builder
public record RefundIntent(String reservationId, String orderId, String reason) {
}
