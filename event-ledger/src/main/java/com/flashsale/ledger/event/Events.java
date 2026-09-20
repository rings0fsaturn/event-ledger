package com.flashsale.ledger.event;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record Events(
		String accountId,
		String idempotencyKey,
		EventType eventType,
		OffsetDateTime occuredAt,
		OffsetDateTime recievedAt,
		String payload
		) {}
