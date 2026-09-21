package com.flashsale.ledger.repo;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.flashsale.ledger.event.Events;

public interface EventsRepo {
	Boolean isDuplicateEvent(String accountId, String idempotencyKey);
	int createEvent(Events event);
	Optional<Events> getEventByKeys(String accountId, String idempotencyKey); 
}
