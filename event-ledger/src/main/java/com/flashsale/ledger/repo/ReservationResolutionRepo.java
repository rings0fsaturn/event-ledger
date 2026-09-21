package com.flashsale.ledger.repo;

import com.flashsale.ledger.reservation.ReservationResolution;

public interface ReservationResolutionRepo {
	
	Integer createExpiredReservationResolution(ReservationResolution res);

}
