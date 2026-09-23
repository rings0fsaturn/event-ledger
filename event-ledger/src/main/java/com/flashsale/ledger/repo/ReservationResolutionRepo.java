package com.flashsale.ledger.repo;

import com.flashsale.ledger.reservation.ReservationResolution;
import com.flashsale.ledger.reservation.ReservationResolutionOutcome;

public interface ReservationResolutionRepo {
	
	

	Integer createReservationResolution(ReservationResolution res);

	ReservationResolutionOutcome getOutcomeFromReservationResolutionById(String reservationId);

	int clearReservationResolutionById(String reservationId);

}
