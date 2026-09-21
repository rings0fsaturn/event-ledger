package com.flashsale.ledger.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.flashsale.ledger.reservation.Reservation;

public interface ReservationRepo {
		 
	 Reservation createReservation(MapSqlParameterSource params);
	 
	 List<String> getUnresolvedExpiringReservationIds(Integer batchSize);
	 
	 Optional<Reservation> getUnresolvedExpiringReservationById(String reservationId);

	 Integer setResolvedAtToNowForReservationID(String reservationId);

}
