package com.flashsale.ledger.repo.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.repo.RepoQueries;
import com.flashsale.ledger.repo.ReservationResolutionRepo;
import com.flashsale.ledger.reservation.ReservationResolution;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReservationResolutionRepoImpl implements ReservationResolutionRepo {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	
	@Override
	public Integer createExpiredReservationResolution(ReservationResolution res) {
		MapSqlParameterSource param = new MapSqlParameterSource()
				.addValue("reservation_id", res.reservationId())
				.addValue("outcome", res.outcome().getValue())
				.addValue("event_key", res.eventKey());
		return namedJdbcTemplate.update(RepoQueries.createReservationResolution, param);	
	}

}
