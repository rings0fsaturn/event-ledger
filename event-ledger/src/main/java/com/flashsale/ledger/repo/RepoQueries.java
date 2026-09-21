package com.flashsale.ledger.repo;

import lombok.Getter;

@Getter
public class RepoQueries {
	
//	UTILITIES
	public static final String getDBClockNow = "SELECT now()";
	
//	RESERVATION Queries
	public static final String createReservationAndReturnRow = "INSERT INTO reservations (reservation_id, order_id, account_id, sku, quantity, expires_at) "
			+ "VALUES (:reservation_id, :order_id, :account_id, :sku, :quantity, "
			+ "now() + make_interval(secs => :ttl_secs)) RETURNING *";

	public static final String getExpiringUnresolvedReservationIdWithLimit = "SELECT reservation_id FROM reservations WHERE expires_at <= now() AND resolved_at IS NULL "
			+ "ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED";
	
	public static final String getUnresolvedExpiringReservationByIdLockedForUpdate= "SELECT reservation_id, order_id, account_id, sku, quantity, expires_at FROM reservations "
	        + "WHERE reservation_id = ? AND resolved_at IS NULL AND expires_at <= now() FOR UPDATE";

	public static final String setResolvedAtToNowForReservationID ="UPDATE reservations SET resolved_at = now() WHERE reservation_id = ? AND resolved_at IS NULL";
	
	
//	Reservation_resolution Queries
	public static final String createReservationResolution = "INSERT INTO reservation_resolution(reservation_id, outcome, event_key) "
			+ "VALUES (:reservation_id, :outcome, :event_key)";

// 	EVENTS queries
	public static final String getEventsByAccountIdAndIdempotencyKey = "SELECT event_type, occurred_at, received_at, payload "
			+ "from events where account_id=? and idempotency_key=?";
	
	public static final String eventExistsByAccountIdAndIdempotencyKey = "SELECT EXISTS(SELECT 1 FROM events WHERE account_id=? and idempotency_key=?)";
	
	public static final String createEvent = "INSERT INTO events (account_id, idempotency_key, event_type, occurred_at, payload) "
			+ "VALUES (:account_id, :idempotency_key, :event_type, now(), CAST(:payload AS jsonb)) "
			+ "ON CONFLICT (account_id, idempotency_key) DO NOTHING";
	

//	Stock level
	public static final String getStockLevelBySkuLockedForUpdate = "SELECT total, reserved, sold, reservation_ttl_seconds "
			+ "from stock_levels where sku=? FOR UPDATE";

	public static final String incrementReservedStockLevelByQuantity = "UPDATE stock_levels set reserved = reserved + :quantity "
			+ "where sku=:sku and total - reserved - sold >= :quantity";
	
	public static final String decrementReservedStockLevelByQuantity = "UPDATE stock_levels set reserved = reserved - :quantity "
			+ "where sku=:sku and reserved >= :quantity";
}
