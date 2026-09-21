package com.flashsale.ledger.sweep;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record ReservationExpired(String reservationId, String orderId, String accountId, String sku, Integer quantity,
		OffsetDateTime expiredAt) {

}
