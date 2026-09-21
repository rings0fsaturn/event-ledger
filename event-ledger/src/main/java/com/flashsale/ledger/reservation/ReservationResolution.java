package com.flashsale.ledger.reservation;

import lombok.Builder;

@Builder
public record ReservationResolution(String reservationId,
		ReservationResolutionOutcome outcome,
		String eventKey) {

}
