package com.flashsale.ledger.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.ledger.reservation.Outcome;
import com.flashsale.ledger.reservation.ReservationService;
import com.flashsale.ledger.reservation.ReserveRequest;
import com.flashsale.ledger.reservation.ReserveResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequiredArgsConstructor
public class OrderController {

	private final ObjectMapper mapper;
	private final ReservationService reservationService;

	@PostMapping("/orders")
	public ResponseEntity<String> order(@RequestBody @Valid ReserveRequest req, 
			@RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyHeader){
		
		if(!StringUtils.hasText(idempotencyHeader) && !StringUtils.hasText(req.idempotencyKey()))
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Idempotency-Key missing");
			
		ReserveRequest reqForReservation = req;
		
		if(StringUtils.hasText(idempotencyHeader))
			reqForReservation = ReserveRequest.builder()
				.accountId(req.accountId())
				.orderId(req.orderId())
				.idempotencyKey(idempotencyHeader)
				.sku(req.sku())
				.quantity(req.quantity())
			.build();
		
		
		ReserveResult res = reservationService.reserve(reqForReservation);
		HttpStatus status = decideStatusCodeFromReserveOutcome(res.outcome());
		
		return ResponseEntity.status(status).body(mapper.writeValueAsString(res.reservation()));
	}

	private HttpStatus decideStatusCodeFromReserveOutcome(Outcome outcome) {
		if(outcome == Outcome.RESERVED)
			return HttpStatus.CREATED;
		if(outcome == Outcome.SOLD_OUT)
			return HttpStatus.CONFLICT;
		
		return HttpStatus.OK;
	}
}
