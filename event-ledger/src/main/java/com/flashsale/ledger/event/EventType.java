package com.flashsale.ledger.event;

import java.util.HashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

import lombok.Getter;

@Getter
public enum EventType {
	ORDER_PLACED("OrderPlaced"), 
	INVENTORY_RESERVED("InventoryReserved"), 
	PAYMENT_CAPTURED("PaymentCaptured"), 
	REFUND_ISSUED("RefundIssued"), 
	RESERVATION_EXPIRED("ReservationExpired");

	private final String eventTypeFromDB;
	private static final Map<String, EventType> BY_DB_VALUE = new HashMap<>();
	
	static {
		for(EventType t: values()) {
			BY_DB_VALUE.put(t.eventTypeFromDB, t);
		}
	}
	
	EventType(String value) {
		this.eventTypeFromDB = value;
	}
	
	public static EventType fromDbValue(String dbValue) {
		if(!StringUtils.hasLength(dbValue))
			throw new IllegalArgumentException("event_type is null");
		EventType eventType = BY_DB_VALUE.get(dbValue);
		if(eventType == null)
			throw new IllegalArgumentException("Unknown event_type:"+dbValue);
		return eventType;
			
	}
}
