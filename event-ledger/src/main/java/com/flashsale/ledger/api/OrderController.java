package com.flashsale.ledger.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.ledger.reservation.OrderOutcome;
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
		
		if(res.outcome() == OrderOutcome.ORDER_ALREADY_RESERVED)
			return ResponseEntity.status(status).body(alreadyReservedBody(reqForReservation.orderId()));
		
		return ResponseEntity.status(status).body(mapper.writeValueAsString(res.reservation()));
	}

	private String alreadyReservedBody(String orderId) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,  "An active reservation already exists for this order. "
                + "Retry with the original idempotency key, or wait for the reservation to expire.");
		pd.setTitle("Order Already Reserved");
		pd.setProperty("order_id", orderId);
		return mapper.writeValueAsString(pd);
	}

	private HttpStatus decideStatusCodeFromReserveOutcome(OrderOutcome outcome) {
		if(outcome == OrderOutcome.RESERVED)
			return HttpStatus.CREATED;
		if(outcome == OrderOutcome.SOLD_OUT || outcome == OrderOutcome.ORDER_ALREADY_RESERVED)
			return HttpStatus.CONFLICT;
		
		return HttpStatus.OK;
	}
}
