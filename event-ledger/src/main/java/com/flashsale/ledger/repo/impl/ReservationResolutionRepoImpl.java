package com.flashsale.ledger.repo.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.repo.RepoQueries;
import com.flashsale.ledger.repo.ReservationResolutionRepo;
import com.flashsale.ledger.reservation.ReservationResolution;
import com.flashsale.ledger.reservation.ReservationResolutionOutcome;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReservationResolutionRepoImpl implements ReservationResolutionRepo {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	
	@Override
	public Integer createReservationResolution(ReservationResolution res) {
		MapSqlParameterSource param = new MapSqlParameterSource()
				.addValue("reservation_id", res.reservationId())
				.addValue("outcome", res.outcome().getValue())
				.addValue("event_key", res.eventKey());
		return namedJdbcTemplate.update(RepoQueries.createReservationResolution, param);	
	}

	
	@Override
	public ReservationResolutionOutcome getOutcomeFromReservationResolutionById(String reservationId) {
		String outcome = jdbcTemplate.queryForObject(RepoQueries.getOutcomeFromReservationResolutionById, String.class, reservationId);
		return ReservationResolutionOutcome.fromDbValue(outcome);
	}

	@Override
	public int clearReservationResolutionById(String reservationId) {
		return jdbcTemplate.update(RepoQueries.clearReservationResolutionById, reservationId);
	}
}
