package com.flashsale.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import com.flashsale.ledger.sweep.ExpiryOutcome;
import com.flashsale.ledger.sweep.ReservationExpiryService;

import tools.jackson.databind.ObjectMapper;

// Phase 2 gate P8: expiry releases stock exactly once, and a later payment is
// rejected (spec section 8 P8, section 6(d)).
// Two halves, both required:
//   1. the scheduled sweeper, armed at production cadence, releases the unit
//   2. expireOne called directly, in isolation, proves the gate is idempotent
// The clock is read from the DB, never from Java, so a drifted WSL2 clock does
// not make these tests lie (spec section 4).
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpirySweeperTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ReservationExpiryService expiryService;

	@Autowired
	ObjectMapper mapper;

	private RestClient rest;

	@BeforeEach
	void setUpClient() {
		rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	// P8: a reservation left to expire returns its unit exactly once, and the
	// ledger records why. The event key is derived from the reservation id, so it
	// is stable and a re-sweep cannot append a second copy.
	// DDIA Ch.11 "Idempotence".
	@Test
	void expiredReservationReleasesStockExactlyOnce() throws Exception {
		String sku = "TEST-P8-" + shortId();
		seedSku(sku, 5, 2);
		String account = "acct-p8-" + shortId();
		String key = "key-p8-" + shortId();
		String orderId = "ord-p8-" + shortId();

		ResponseEntity<String> reserved = postOrder(account, orderId, sku, 1, key);
		assertEquals(201, reserved.getStatusCode().value(), "first attempt reserves");
		assertEquals(1, intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku));
		String reservationId = jdbc.queryForObject(
				"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);

		awaitResolved(reservationId);

		assertEquals(0, intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku),
				"expiry returned the unit once");
		assertEquals(1, intAt(
				"SELECT COUNT(*) FROM reservation_resolution WHERE reservation_id = ? AND outcome = 'expired'",
				reservationId), "exactly one expired resolution");
		assertEquals(1, intAt(
				"SELECT COUNT(*) FROM events WHERE idempotency_key = ? AND event_type = 'ReservationExpired'",
				reservationId + ":expired"), "exactly one expiry event");
	}

	// P8: the frozen payload shape from the plan's section 0.4 - six fields,
	// and expired_at is an observation, not the deadline. It must therefore be
	// >= the reservation's expires_at, which is a check expires_at could never
	// fail. This is the assertion that catches a sweeper expiring early.
	@Test
	void expiryPayloadHasFrozenShapeAndFiresAfterDeadline() throws Exception {
		String sku = "TEST-P8F-" + shortId();
		seedSku(sku, 5, 2);
		String account = "acct-p8f-" + shortId();
		String key = "key-p8f-" + shortId();
		String orderId = "ord-p8f-" + shortId();

		assertEquals(201, postOrder(account, orderId, sku, 1, key).getStatusCode().value());
		String reservationId = jdbc.queryForObject(
				"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);
		awaitResolved(reservationId);

		// payload is jsonb, so ask the driver for text and parse it rather than
		// fighting PGobject.
		String payload = jdbc.queryForObject(
				"SELECT payload::text FROM events WHERE idempotency_key = ?", String.class, reservationId + ":expired");

		assertEquals(List.of("account_id", "expired_at", "order_id", "quantity", "reservation_id", "sku"),
				mapper.readTree(payload).propertyNames().stream().sorted().toList(),
				"exactly the six frozen fields, no more");

		assertTrue(jdbc.queryForObject(
				"SELECT (e.payload->>'expired_at')::timestamptz >= r.expires_at FROM events e"
				+ " JOIN reservations r ON r.reservation_id = e.payload->>'reservation_id'"
				+ " WHERE e.idempotency_key = ?",
				Boolean.class, reservationId + ":expired"),
				"expired_at is observed at or after the deadline, never before");

		// Both clocks come from the DB, so these must be identical, not merely close.
		assertEquals(0, intAt(
				"SELECT COUNT(*) FROM events WHERE idempotency_key = ?"
				+ " AND (payload->>'expired_at')::timestamptz <> occurred_at",
				reservationId + ":expired"),
				"payload expired_at agrees exactly with the event envelope");
	}

	// P8: the gate makes re-expiry a no-op. Second call returns without touching
	// stock, events or resolutions, which is what makes the sweeper safe to run
	// against a row a human already resolved.
	@Test
	void reSweepIsANoOp() throws Exception {
		String sku = "TEST-P8R-" + shortId();
		seedSku(sku, 5, 2);
		String account = "acct-p8r-" + shortId();
		String key = "key-p8r-" + shortId();
		String orderId = "ord-p8r-" + shortId();

		assertEquals(201, postOrder(account, orderId, sku, 1, key).getStatusCode().value());
		String reservationId = jdbc.queryForObject(
				"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);
		awaitResolved(reservationId);

		int reservedBefore = intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku);
		int eventsBefore = intAt("SELECT COUNT(*) FROM events WHERE idempotency_key = ?", reservationId + ":expired");

		// Already resolved, so the guard query finds nothing and the gate is not reached.
		assertEquals(ExpiryOutcome.ALREADY_RESOLVED, expiryService.expireOne(reservationId),
				"a resolved reservation is not expirable again");

		// The other half: a reservation still inside its TTL is also ALREADY_RESOLVED,
		// because expiry is never early. Without this the no-op proof is vacuous.
		String liveId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO reservations (reservation_id, order_id, account_id, sku, quantity, expires_at)"
				+ " VALUES (?, ?, ?, ?, 1, now() + make_interval(secs => 900))",
				liveId, "ord-live-" + shortId(), account, sku);
		assertEquals(ExpiryOutcome.ALREADY_RESOLVED, expiryService.expireOne(liveId),
				"a reservation inside its TTL is not expirable yet");

		assertEquals(reservedBefore, intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku),
				"stock unchanged by the no-op");
		assertEquals(eventsBefore, intAt("SELECT COUNT(*) FROM events WHERE idempotency_key = ?",
				reservationId + ":expired"), "no second expiry event");
		assertEquals(1, intAt("SELECT COUNT(*) FROM reservation_resolution WHERE reservation_id = ?", reservationId),
				"still one resolution");
	}

	// The release path has no lower bound, so a double release would drive
	// `reserved` negative and make I1 (reserved + sold <= total) easier to satisfy
	// rather than harder. This pins that the gate, not the UPDATE, is what keeps
	// it from happening. Spec section 3 I1.
	@Test
	void reservedNeverGoesNegativeAcrossRepeatedSweeps() throws Exception {
		String sku = "TEST-P8N-" + shortId();
		seedSku(sku, 5, 2);
		String account = "acct-p8n-" + shortId();
		String key = "key-p8n-" + shortId();
		String orderId = "ord-p8n-" + shortId();

		assertEquals(201, postOrder(account, orderId, sku, 1, key).getStatusCode().value());
		String reservationId = jdbc.queryForObject(
				"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);
		awaitResolved(reservationId);

		for (int i = 0; i < 3; i++) {
			expiryService.expireOne(reservationId);
		}

		int reserved = intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku);
		int sold = intAt("SELECT sold FROM stock_levels WHERE sku = ?", sku);
		int total = intAt("SELECT total FROM stock_levels WHERE sku = ?", sku);
		assertTrue(reserved >= 0, "reserved never goes negative, got " + reserved);
		assertTrue(reserved + sold <= total, "I1 holds: reserved + sold <= total");
		assertEquals(0, reserved, "no phantom units left reserved");
	}

	// -- helpers ------------------------------------------------------------

	// Hand-rolled poll: Awaitility is not on this module's classpath.
	private void awaitResolved(String reservationId) throws Exception {
		long deadline = System.currentTimeMillis() + 30_000;
		while (System.currentTimeMillis() < deadline) {
			if (intAt("SELECT COUNT(*) FROM reservations WHERE reservation_id = ? AND resolved_at IS NOT NULL",
					reservationId) == 1) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("sweeper did not resolve reservation " + reservationId
				+ " within 30s; is @EnableScheduling present and sweeper.fixed-delay-ms small?");
	}

	private ResponseEntity<String> postOrder(String account, String orderId, String sku, int quantity, String key) {
		Map<String, Object> body = Map.of(
				"account_id", account, "order_id", orderId, "sku", sku,
				"quantity", quantity, "idempotency_key", key);
		ResponseEntity<String> response = rest.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.onStatus(status -> true, (request, res) -> {
				}).toEntity(String.class);
		assertNotNull(response.getBody(), "sold out and conflicts return a body, not a transport failure");
		return response;
	}

	private void seedSku(String sku, int total, int ttlSeconds) {
		jdbc.update("INSERT INTO stock_levels (sku, total, reserved, sold, reservation_ttl_seconds)"
				+ " VALUES (?, ?, 0, 0, ?) ON CONFLICT (sku) DO NOTHING", sku, total, ttlSeconds);
	}

	private int intAt(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private static String shortId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
