package com.flashsale.ledger.reservation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.ledger.event.EventType;
import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.reservation.repo.ReservationRepo;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReservationService {

	private final ReservationRepo reservationRepo;
	private final ObjectMapper objectMapper;
	
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ReserveResult reserve(ReserveRequest request) {
		
		StockLevel stockLevel = reservationRepo.lockStockRow(request.sku());
		
		Optional<Events> eventO = reservationRepo.getEventByKeys(request.accountId(), request.idempotencyKey());
		
		//	Duplicate event	
		if(eventO.isPresent()) {
			Events event = eventO.get();
			return ReserveResult.builder()
					.outcome(Outcome.DUPLICATE)
					.reservation(objectMapper.readValue(event.payload(), Reservation.class))
					.build();
		}
		// First Time event
		Integer rowsUpdated = reservationRepo.incrementReservedCountInStockLevel(request.sku(), request.quantity());
		
		// No stock available
		if(rowsUpdated == 0) {
			return ReserveResult.builder()
					.outcome(Outcome.SOLD_OUT)
					.build();			
		}
			
		// Make Reservation Insert
		createReservation(request,stockLevel.reservationTTLSeconds());
		Reservation currReservation = getReservationByOrderId(request.orderId()); 
		// Create event
		createEvent(request,currReservation);
		
		return ReserveResult.builder()
				.outcome(Outcome.RESERVED)
				.reservation(currReservation)
				.build();
	}

	private void createEvent(ReserveRequest request, Reservation currReservation) {
		String reservationJsonPayload = objectMapper.writeValueAsString(currReservation);
		MapSqlParameterSource param = new MapSqlParameterSource()
				.addValue("account_id", request.accountId())
				.addValue("idempotency_key", request.idempotencyKey())
				.addValue("event_type", EventType.INVENTORY_RESERVED.getEventTypeFromDB())
				.addValue("payload", reservationJsonPayload)
				;
		int rowsInserted = reservationRepo.createEvent(param);
		if(rowsInserted != 1)
			throw new RuntimeException("Cannot Create EVENT");
		
	}

	private Reservation getReservationByOrderId(String orderId) {
		Optional<Reservation> resO =  reservationRepo.getReservationByOrderId(orderId);
		if(resO.isPresent())
			return resO.get();
		throw new RuntimeException("No Reservation found");
	}
	
	public void createReservation(ReserveRequest request, Integer reservationTTLSeconds) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("reservation_id", UUID.randomUUID().toString())
				.addValue("order_id", request.orderId())
				.addValue("account_id", request.accountId())
				.addValue("sku", request.sku())
				.addValue("quantity", request.quantity())
				.addValue("ttl_secs", reservationTTLSeconds);
		int rowsInserted = reservationRepo.createReservation(params);
		if(rowsInserted != 1)
			throw new RuntimeException("Cannot Create Reservation");
		
	}

	

	
	
}
