package com.flashsale.ledger.reservation.repo.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.event.EventType;
import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.exception.UnknownSkuException;
import com.flashsale.ledger.reservation.Reservation;
import com.flashsale.ledger.reservation.StockLevel;
import com.flashsale.ledger.reservation.repo.ReservationRepo;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReservationRepoImpl implements ReservationRepo{
	
	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	
	public StockLevel lockStockRow(String sku) {
		String sql = "SELECT total, reserved, sold, reservation_ttl_seconds "
				+ "from stock_levels where sku=? FOR UPDATE";
		try {			
			return jdbcTemplate.queryForObject(
					sql, 
					(rs,rowNum)-> StockLevel.builder()
					.total(rs.getInt("total"))
					.reserved(rs.getInt("reserved"))
					.sold(rs.getInt("sold"))
					.reservationTTLSeconds(rs.getInt("reservation_ttl_seconds"))
					.build(),
					sku
					);
		}catch(EmptyResultDataAccessException e) {
			throw new UnknownSkuException(sku);
		}
	}
	
	public Optional<Events> getEventByKeys(String accountId, String idempotencyKey) {
		String sql = "SELECT event_type, occurred_at, received_at, payload from events where account_id=? and idempotency_key=?";
		
		List<Events> events =  jdbcTemplate.query(sql, (rs, rowNum)-> Events.builder()
					.accountId(accountId)
					.idempotencyKey(idempotencyKey)
					.eventType(EventType.fromDbValue(rs.getString("event_type")))
					.occuredAt(rs.getObject("occurred_at", OffsetDateTime.class))
					.recievedAt(rs.getObject("received_at", OffsetDateTime.class))
					.payload(rs.getString("payload"))
					.build(), accountId,idempotencyKey);
		
		return Optional.ofNullable(DataAccessUtils.singleResult(events));
		
		
	}
	
	public Integer incrementReservedCountInStockLevel(@NotBlank(message = "SKU cannot be empty") String sku, Integer quantity) {
		String sql = "UPDATE stock_levels set reserved = reserved + :quantity where sku=:sku and total - reserved - sold >= :quantity";
		MapSqlParameterSource param = new MapSqlParameterSource().addValue("sku", sku).addValue("quantity", quantity);
		return namedJdbcTemplate.update(sql, param);	
	}
	

	public Boolean isDuplicateEvent(@NotBlank(message = "accountId cannot be empty") String accountId, 
			@NotBlank(message = "idempotencyKey cannot be empty") String idempotencyKey) {
		String sql = "SELECT EXISTS(SELECT 1 FROM events WHERE account_id=? and idempotency_key=?)";
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, accountId, idempotencyKey));
	}
	
	public Integer createReservation(MapSqlParameterSource params) {
		String sql = "INSERT INTO reservations (reservation_id, order_id, account_id, sku, quantity, expires_at) "
				+ "VALUES (:reservation_id, :order_id, :account_id, :sku, :quantity, "
				+ "now() + make_interval(secs => :ttl_secs))";
		
		return namedJdbcTemplate.update(sql,params);
		
	}

	public Optional<Reservation> getReservationByOrderId(@NotBlank(message = "OrderId cannot be blank") String orderId) {
		String sql = "SELECT * from reservations where order_id=?";
		List<Reservation> reservations =  jdbcTemplate.query(sql, new DataClassRowMapper<>(Reservation.class),orderId);
		if(reservations != null && !reservations.isEmpty())
			return Optional.of(reservations.get(0));
		return Optional.empty();
	}

	public int createEvent(MapSqlParameterSource param) {
		String sql = "INSERT INTO events (account_id, idempotency_key, event_type, occurred_at, payload) "
				+ "VALUES (:account_id, :idempotency_key, :event_type, now(), CAST(:payload AS jsonb))"
				+ "ON CONFLICT (account_id, idempotency_key) DO NOTHING";
		
		
		return namedJdbcTemplate.update(sql, param);
	}
	
	
}
