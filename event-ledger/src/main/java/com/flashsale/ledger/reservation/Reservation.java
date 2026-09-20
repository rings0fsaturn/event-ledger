package com.flashsale.ledger.reservation;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record Reservation(String reservationId, String orderId, String accountId, String sku, Integer quantity,
		OffsetDateTime expiresAt) {

}
