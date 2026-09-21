package com.flashsale.ledger.repo.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.repo.RepoQueries;
import com.flashsale.ledger.repo.ReservationRepo;
import com.flashsale.ledger.reservation.Reservation;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReservationRepoImpl implements ReservationRepo{
	
	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	
	@Override
	public Reservation createReservation(MapSqlParameterSource params) {
		List<Reservation> res= namedJdbcTemplate.query(RepoQueries.createReservationAndReturnRow
				,params
				, new DataClassRowMapper<>(Reservation.class));
		if(res == null || res.isEmpty())
			throw new RuntimeException("Unable to create a Reservation");
		
		return res.get(0);
		
	}
	
	public List<String> getUnresolvedExpiringReservationIds(Integer batchSize){
		return jdbcTemplate.queryForList(RepoQueries.getExpiringUnresolvedReservationIdWithLimit, String.class,batchSize);
	}
	
	public Optional<Reservation> getUnresolvedExpiringReservationById(String reservationId) {
		List<Reservation> reservations =  jdbcTemplate.query(RepoQueries.getUnresolvedExpiringReservationByIdLockedForUpdate, 
				new DataClassRowMapper<>(Reservation.class),
				reservationId);
		
		if(reservations != null && !reservations.isEmpty())
			return Optional.of(reservations.get(0));
		return Optional.empty();
	}
	
	@Override
	public Integer setResolvedAtToNowForReservationID(String reservationId) {
		return jdbcTemplate.update(RepoQueries.setResolvedAtToNowForReservationID, reservationId);
	}
	
}
