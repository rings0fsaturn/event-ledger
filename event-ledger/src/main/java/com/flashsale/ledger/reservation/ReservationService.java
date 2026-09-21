package com.flashsale.ledger.reservation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.ledger.event.EventType;
import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.repo.EventsRepo;
import com.flashsale.ledger.repo.ReservationRepo;
import com.flashsale.ledger.repo.StockLevelRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

	private final ReservationRepo reservationRepo;
	private final StockLevelRepo stockLevelRepo;
	private final EventsRepo eventsRepo;
	private final ObjectMapper objectMapper;
	
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ReserveResult reserve(ReserveRequest request) {
		
		StockLevel stockLevel = stockLevelRepo.lockStockRow(request.sku());
		
		Optional<Events> eventO = eventsRepo.getEventByKeys(request.accountId(), request.idempotencyKey());
		
		//	Duplicate event	
		if(eventO.isPresent()) {
			Events event = eventO.get();
			return ReserveResult.builder()
					.outcome(OrderOutcome.DUPLICATE)
					.reservation(objectMapper.readValue(event.payload(), Reservation.class))
					.build();
		}
		// First Time event
		Integer rowsUpdated = stockLevelRepo.incrementReservedCountInStockLevel(request.sku(), request.quantity());
		
		// No stock available
		if(rowsUpdated == 0) {
			return ReserveResult.builder()
					.outcome(OrderOutcome.SOLD_OUT)
					.build();			
		}
			
		// Make Reservation Insert
		Reservation currReservation;
		try {
			currReservation = createReservation(request,stockLevel.reservationTTLSeconds());			
		}catch(DuplicateKeyException e) {
			log.warn("order already holds a live reservation: order_id: "+request.orderId(), e.getCause());
			return ReserveResult.builder()
					.outcome(OrderOutcome.ORDER_ALREADY_RESERVED)
					.build();
		}
		
		// Create event
		createEvent(request,currReservation);
		
		return ReserveResult.builder()
				.outcome(OrderOutcome.RESERVED)
				.reservation(currReservation)
				.build();
	}

	private void createEvent(ReserveRequest request, Reservation currReservation) {
		String reservationJsonPayload = objectMapper.writeValueAsString(currReservation);
		
		Events event = Events.builder()
				.accountId(request.accountId())
				.idempotencyKey(request.idempotencyKey())
				.eventType(EventType.INVENTORY_RESERVED)
				.payload(reservationJsonPayload)
				.build();
		
		int rowsInserted = eventsRepo.createEvent(event);
		if(rowsInserted != 1)
			throw new RuntimeException("Cannot Create EVENT");
		
	}
	
	public Reservation createReservation(ReserveRequest request, Integer reservationTTLSeconds) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("reservation_id", UUID.randomUUID().toString())
				.addValue("order_id", request.orderId())
				.addValue("account_id", request.accountId())
				.addValue("sku", request.sku())
				.addValue("quantity", request.quantity())
				.addValue("ttl_secs", reservationTTLSeconds);

		
		return reservationRepo.createReservation(params);						
		
		
	}

	

	
	
}
