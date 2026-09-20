package com.flashsale.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

// Phase 1 Step 4: HTTP-level contract tests for POST /orders.
// Spec .work/spec/event-ledger.md; DDIA refs inline per test.
// Uses RestClient (spring-web, already on the classpath) instead of
// TestRestTemplate, whose Boot 4.1 auto-configuration needs the
// spring-boot-restclient artifact this module does not pull in.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationApiTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	private RestClient rest;

	@BeforeEach
	void setUpClient() {
		rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	// P1 / I3: duplicate delivery is a no-op (spec section 8 P1, section 5).
	// Mechanism: events PK (account_id, idempotency_key) + ON CONFLICT DO NOTHING;
	// the retry returns the stored reservation instead of doing work.
	// DDIA Ch.11 "Idempotence".
	@Test
	void duplicatePostIsNoOp() {
		String sku = "TEST-P1-" + shortId();
		seedSku(sku, 10);
		String account = "acct-p1-" + shortId();
		String key = "key-p1-" + shortId();
		System.out.println("[duplicatePostIsNoOp] sku=" + sku + " account=" + account + " key=" + key);

		ResponseEntity<String> first = postOrder(account, sku, 1, key);
		System.out.println("[duplicatePostIsNoOp] first status=" + first.getStatusCode().value() + " body=" + first.getBody());
		assertEquals(201, first.getStatusCode().value(), "first attempt reserves");

		for (int i = 0; i < 4; i++) {
			ResponseEntity<String> retry = postOrder(account, sku, 1, key);
			System.out.println("[duplicatePostIsNoOp] retry " + (i + 1) + " status=" + retry.getStatusCode().value() + " body=" + retry.getBody());
			assertEquals(200, retry.getStatusCode().value(), "retry " + (i + 1) + " is a duplicate");
			assertEquals(first.getBody(), retry.getBody(), "duplicate returns the existing reservation");
		}

		Integer events = jdbc.queryForObject(
				"SELECT COUNT(*) FROM events WHERE account_id = ? AND idempotency_key = ?",
				Integer.class, account, key);
		assertEquals(1, events, "one event row for the key");
		Integer reservations = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reservations WHERE account_id = ? AND sku = ?",
				Integer.class, account, sku);
		assertEquals(1, reservations, "one reservation for the buyer");
		Integer reserved = jdbc.queryForObject(
				"SELECT reserved FROM stock_levels WHERE sku = ?", Integer.class, sku);
		assertEquals(1, reserved, "stock moved by exactly one unit");
	}

	// Phantom: SELECT FOR UPDATE cannot lock a row that does not exist, so the
	// code must fail loudly rather than inventing the row (spec 3.1, 3.2 one rule).
	// DDIA Ch.7 "Phantoms", "Materializing conflicts".
	@Test
	void missingStockRowFailsLoudly() {
		String sku = "NOPE-" + shortId(); // deliberately never seeded
		String account = "acct-ph-" + shortId();
		String key = "key-ph-" + shortId();

		System.out.println("[missingStockRowFailsLoudly] sku=" + sku + " account=" + account + " key=" + key);
		ResponseEntity<String> response = postOrder(account, sku, 1, key);
		System.out.println("[missingStockRowFailsLoudly] status=" + response.getStatusCode().value() + " body=" + response.getBody());
		assertEquals(500, response.getStatusCode().value(), "missing stock row is a server bug, not sold out");

		Integer reservations = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reservations WHERE sku = ?", Integer.class, sku);
		assertEquals(0, reservations, "no reservation for an unknown SKU");
		Integer stockRows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM stock_levels WHERE sku = ?", Integer.class, sku);
		assertEquals(0, stockRows, "must not invent the row on demand");
	}

	// P2 skeleton / I1: two buyers race for the last unit; exactly one wins
	// (spec 3.2 timeline, 8 P2). Loser blocks on FOR UPDATE, then its guarded
	// UPDATE re-evaluates to 0 rows under READ COMMITTED and returns 409.
	// DDIA Ch.7 "Characterizing write skew".
	@Test
	void twoThreadsRaceOneUnit() throws Exception {
		String sku = "TEST-RACE-" + shortId();
		seedSku(sku, 1);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		// One task per order sharing the same start gate.
		Callable<ResponseEntity<String>> taskA = () -> {
			ready.countDown();
			start.await(10, TimeUnit.SECONDS);
			return postOrder("acct-r1-" + shortId(), sku, 1, "key-r1-" + shortId());
		};
		Callable<ResponseEntity<String>> taskB = () -> {
			ready.countDown();
			start.await(10, TimeUnit.SECONDS);
			return postOrder("acct-r2-" + shortId(), sku, 1, "key-r2-" + shortId());
		};
		Future<ResponseEntity<String>> futureA = pool.submit(taskA);
		Future<ResponseEntity<String>> futureB = pool.submit(taskB);
		assertTrue(ready.await(10, TimeUnit.SECONDS), "both racers arrived");
		start.countDown();

		ResponseEntity<String> responseA = futureA.get(30, TimeUnit.SECONDS);
		ResponseEntity<String> responseB = futureB.get(30, TimeUnit.SECONDS);
		pool.shutdown();
		System.out.println("[twoThreadsRaceOneUnit] sku=" + sku + " statusA=" + responseA.getStatusCode().value() + " bodyA=" + responseA.getBody());
		System.out.println("[twoThreadsRaceOneUnit] sku=" + sku + " statusB=" + responseB.getStatusCode().value() + " bodyB=" + responseB.getBody());

		int statusA = responseA.getStatusCode().value();
		int statusB = responseB.getStatusCode().value();
		assertTrue(statusA == 201 && statusB == 409 || statusA == 409 && statusB == 201,
				"exactly one winner: got " + statusA + " and " + statusB);

		Map<String, Object> stock = jdbc.queryForMap(
				"SELECT total, reserved, sold FROM stock_levels WHERE sku = ?", sku);
		int total = ((Number) stock.get("total")).intValue();
		int reserved = ((Number) stock.get("reserved")).intValue();
		int sold = ((Number) stock.get("sold")).intValue();
		assertTrue(reserved + sold <= total, "I1 holds: reserved + sold <= total");
		assertEquals(1, reserved + sold, "exactly one unit held");
		Integer reservations = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reservations WHERE sku = ?", Integer.class, sku);
		assertEquals(1, reservations, "exactly one reservation row");
	}

	// Quantity guard: predicate and increment must use the same :quantity param
	// (spec 3.2 guard, I1). At quantity 1 the predicates ">= 1" and ">= :quantity"
	// are indistinguishable, so only quantity > 1 proves the fix.
	// DDIA Ch.7 "Characterizing write skew".
	@Test
	void quantityGreaterThanStockIsRejected() {
		String sku = "TEST-QTY-" + shortId();
		seedSku(sku, 2);
		String account = "acct-qty-" + shortId();
		String key = "key-qty-" + shortId();
		System.out.println("[quantityGreaterThanStockIsRejected] sku=" + sku + " account=" + account + " key=" + key);

		ResponseEntity<String> response = postOrder(account, sku, 3, key);
		System.out.println("[quantityGreaterThanStockIsRejected] status=" + response.getStatusCode().value() + " body=" + response.getBody());
		assertEquals(409, response.getStatusCode().value(), "quantity 3 against total 2 is sold out");

		Map<String, Object> stock = jdbc.queryForMap(
				"SELECT total, reserved, sold FROM stock_levels WHERE sku = ?", sku);
		int total = ((Number) stock.get("total")).intValue();
		int reserved = ((Number) stock.get("reserved")).intValue();
		int sold = ((Number) stock.get("sold")).intValue();
		System.out.println("[quantityGreaterThanStockIsRejected] total=" + total + " reserved=" + reserved + " sold=" + sold);
		assertTrue(reserved + sold <= total, "I1 holds: reserved + sold <= total");
		assertEquals(0, reserved + sold, "no units held after rejected quantity");
		Integer reservations = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reservations WHERE sku = ?", Integer.class, sku);
		assertEquals(0, reservations, "no reservation row for rejected quantity");
	}

	private ResponseEntity<String> postOrder(String account, String sku, int quantity, String key) {
		Map<String, Object> body = new HashMap<>();
		body.put("account_id", account);
		body.put("sku", sku);
		body.put("quantity", quantity);
		body.put("idempotency_key", key);
		body.put("order_id", "ord-" + key);
		return rest.post().uri("/orders").header("X-Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.onStatus(status -> true, (request, response) -> {
				}).toEntity(String.class);
	}

	private void seedSku(String sku, int total) {
		jdbc.update("INSERT INTO stock_levels (sku, total, reserved, sold, reservation_ttl_seconds)"
				+ " VALUES (?, ?, 0, 0, 900) ON CONFLICT (sku) DO NOTHING", sku, total);
	}

	private static String shortId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
