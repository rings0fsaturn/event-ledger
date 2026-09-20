package com.flashsale.ledger.reservation.repo;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.reservation.Reservation;
import com.flashsale.ledger.reservation.ReserveRequest;
import com.flashsale.ledger.reservation.StockLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public interface ReservationRepo {
	Boolean isDuplicateEvent(String accountId, String idempotencyKey);
	
	Integer incrementReservedCountInStockLevel(String sku, Integer quantity);
	
	Optional<Events> getEventByKeys(String accountId, String idempotencyKey); 
	
	 StockLevel lockStockRow(String sku);
	 
	 Integer createReservation(MapSqlParameterSource params);

	 Optional<Reservation> getReservationByOrderId(String orderId);

	 int createEvent(MapSqlParameterSource param);
}
