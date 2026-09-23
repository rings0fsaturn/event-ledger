package com.flashsale.ledger.reservation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationResolutionOutcome {
	CAPTURED("captured"),
	REFUNDED("refunded"),
	EXPIRED("expired");
	
	private final String value;
private static final Map<String, ReservationResolutionOutcome> BY_DB_VALUE = new HashMap<>();
	
	static {
		for(ReservationResolutionOutcome t: values()) {
			BY_DB_VALUE.put(t.value, t);
		}
	}
	
	public static ReservationResolutionOutcome fromDbValue(String dbValue) {
		
		if(!StringUtils.hasLength(dbValue))
			throw new IllegalArgumentException("outcome is null");
		
		ReservationResolutionOutcome outcome = BY_DB_VALUE.get(dbValue);
		
		if(outcome == null)
			throw new IllegalArgumentException("Unknown Reservation Resolution Outcome:"+dbValue);
		
		return outcome;
			
	}
}
