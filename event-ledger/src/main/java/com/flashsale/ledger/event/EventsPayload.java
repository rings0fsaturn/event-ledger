package com.flashsale.ledger.event;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

import com.flashsale.ledger.repo.UtilityRepo;
import com.flashsale.ledger.reservation.Reservation;
import com.flashsale.ledger.reservation.ReservationCaptured;
import com.flashsale.ledger.reservation.ReservationExpired;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class EventsPayload {
	
	private final ObjectMapper objectMapper;
	private final UtilityRepo utilityRepo;
	
	
	public Reservation getReservation(String payload) {
		return objectMapper.readValue(payload, Reservation.class);
	}
	
	public ReservationExpired getReservationExpired(String payload) {
		return objectMapper.readValue(payload, ReservationExpired.class);
	}
	
	public ReservationCaptured getReservationCaptured(String payload) {
		return objectMapper.readValue(payload, ReservationCaptured.class);
	}
	
	public String reserved(Reservation res) {
		return objectMapper.writeValueAsString(res);
	}
	
	public String expired(Reservation res, OffsetDateTime expiredAt) {
		ReservationExpired expired = ReservationExpired.builder()
				.accountId(res.accountId())
				.orderId(res.orderId())
				.reservationId(res.reservationId())
				.sku(res.sku())
				.quantity(res.quantity())
				.expiredAt(expiredAt == null ? utilityRepo.getDBClockTime():expiredAt)
				.build();
		
		return objectMapper.writeValueAsString(expired);
	}
	
	public String captured(Reservation res, OffsetDateTime capturedAt) {
		ReservationCaptured captured = ReservationCaptured.builder()
				.accountId(res.accountId())
				.orderId(res.orderId())
				.reservationId(res.reservationId())
				.sku(res.sku())
				.quantity(res.quantity())
				.capturedAt(capturedAt == null ? utilityRepo.getDBClockTime():capturedAt)
				.build();
		
		return objectMapper.writeValueAsString(captured);
	}

}
