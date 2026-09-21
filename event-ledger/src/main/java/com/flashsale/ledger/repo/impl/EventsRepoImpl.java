package com.flashsale.ledger.repo.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.event.EventType;
import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.repo.EventsRepo;
import com.flashsale.ledger.repo.RepoQueries;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EventsRepoImpl implements EventsRepo {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	
	public Optional<Events> getEventByKeys(String accountId, String idempotencyKey) {
		
		List<Events> events =  jdbcTemplate.query(RepoQueries.getEventsByAccountIdAndIdempotencyKey, (rs, rowNum)-> Events.builder()
					.accountId(accountId)
					.idempotencyKey(idempotencyKey)
					.eventType(EventType.fromDbValue(rs.getString("event_type")))
					.occuredAt(rs.getObject("occurred_at", OffsetDateTime.class))
					.recievedAt(rs.getObject("received_at", OffsetDateTime.class))
					.payload(rs.getString("payload"))
					.build(), accountId,idempotencyKey);
		
		return Optional.ofNullable(DataAccessUtils.singleResult(events));	
	}
	
	public Boolean isDuplicateEvent(@NotBlank(message = "accountId cannot be empty") String accountId, 
			@NotBlank(message = "idempotencyKey cannot be empty") String idempotencyKey) {
		
		
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(RepoQueries.eventExistsByAccountIdAndIdempotencyKey, Boolean.class, accountId, idempotencyKey));
	}

	@Override
	public int createEvent(Events event) {
		MapSqlParameterSource param = new MapSqlParameterSource()
				.addValue("account_id", event.accountId())
				.addValue("idempotency_key", event.idempotencyKey())
				.addValue("event_type", event.eventType().getEventTypeFromDB())
				.addValue("payload", event.payload())
				;
		return namedJdbcTemplate.update(RepoQueries.createEvent, param);
	}


}
