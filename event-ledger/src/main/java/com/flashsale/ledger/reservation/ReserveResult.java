package com.flashsale.ledger.reservation;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record ReserveResult(Outcome outcome, Reservation reservation) {

}
