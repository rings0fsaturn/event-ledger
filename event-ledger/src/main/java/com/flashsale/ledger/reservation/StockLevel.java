package com.flashsale.ledger.reservation;

import lombok.Builder;

@Builder
public record StockLevel(
		String sku,
		Integer total,
		Integer reserved,
		Integer sold,
		Integer reservationTTLSeconds
		) {

}
