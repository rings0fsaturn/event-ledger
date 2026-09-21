package com.flashsale.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import com.flashsale.ledger.sweep.ReservationExpiryService;

// STAGED DRILL (D3). The payment path does not exist yet: there is no
// PaymentCaptureService, so the only writer of `captured` is this test. That is
// deliberate - the drill shapes the seam the author will type next, and both
// writers must be shaped by the same drill to be comparable.
//
// The two methods below stand in for the two sides of the race and are the
// contract the real PaymentCaptureService must satisfy:
//   captureForTest      - the would-be webhook: gate insert, then mutate the
//                         ledger. `reserved -> 0, sold + q` in ONE statement.
//   refundForTest       - the webhook-loses branch (spec 6(d)): record the intent
//                         to refund, loudly, and touch nothing else.
// Both are NOT @Transactional: the point is that the DB decides the winner by
// primary key, not that one transaction rolls back.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		// Neutered sweeper: this drill calls expireOne directly on a latch so the
		// interleaving is deliberate rather than racing the 5s tick.
		"sweeper.fixed-delay-ms=3600000",
		"sweeper.initial-delay-ms=3600000"
})
class SweeperRaceTest {

	private static final int ROUNDS = 20;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ReservationExpiryService expiryService;

	private RestClient rest;

	@BeforeEach
	void setUpClient() {
		rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	// D3 / A3: expiry and payment are latched onto the same reservation and fire
	// together. Exactly one may resolve it, both outcomes must actually occur, and
	// the loser must leave its own artefact. A 20-0 split means the drill was
	// designed too gently to prove anything.
	// DDIA Ch.11 "Idempotence" (A3); spec section 6(d), section 8b.6 D3.
	@Test
	void expiryAndPaymentResolveToExactlyOneOutcome() throws Exception {
		String sku = "TEST-D3-" + shortId();
		// TTL 1s, and the scheduled sweeper is neutered above, so the reservation is
		// genuinely expired by the time the latch opens but nothing else has swept it.
		// A long TTL here would make expireOne's guard find no row and return
		// ALREADY_RESOLVED, producing a 20-0 split where no race occurred.
		seedSku(sku, 100, 1);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		AtomicInteger expired = new AtomicInteger();
		AtomicInteger captured = new AtomicInteger();
		AtomicInteger refunds = new AtomicInteger();

		for (int round = 0; round < ROUNDS; round++) {
			String orderId = "ord-d3-" + shortId();
			String key = "key-d3-" + shortId();
			assertEquals(201, postOrder("acct-d3-" + shortId(), orderId, sku, 1, key).getStatusCode().value(),
					"round " + round + " reserves");
			String reservationId = jdbc.queryForObject(
					"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);

			// The reservation must be past its TTL before the two sides are released,
			// otherwise expiry is not a legal contender and there is no race to drill.
			long deadline = System.currentTimeMillis() + 15_000;
			while (System.currentTimeMillis() < deadline
					&& intAt("SELECT COUNT(*) FROM reservations WHERE reservation_id = ? AND expires_at <= now()",
							reservationId) != 1) {
				Thread.sleep(100);
			}
			assertEquals(1, intAt("SELECT COUNT(*) FROM reservations WHERE reservation_id = ? AND expires_at <= now()",
					reservationId), "round " + round + ": reservation is expired before the race starts");

			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);

			// Rounds are biased on purpose. An unbiased latch produced a 20-0 split
			// on this machine: expireOne is a REQUIRES_NEW proxied call, so it pays a
			// connection checkout and a BEGIN before its SELECT, while the payment
			// side's statements autocommit. That bias is a property of the two code
			// shapes, not of the design, and it would leave the expiry branch
			// unexercised. So the first half gives the payment a head start and the
			// second half gives expiry one: both writers still run concurrently, but
			// which one arrives first is deterministic, so BOTH winner branches and
			// both loser branches get asserted.
			boolean paymentHeadStart = round < ROUNDS / 2;
			long expiryDelay = paymentHeadStart ? 50 : 0;
			long paymentDelay = paymentHeadStart ? 0 : 50;

			Callable<String> expirySide = () -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				Thread.sleep(expiryDelay);
				return expiryService.expireOne(reservationId).name();
			};
			Callable<Boolean> paymentSide = () -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				Thread.sleep(paymentDelay);
				return captureAndSettle(reservationId, orderId, sku, 1);
			};

			Future<String> expiryFuture = pool.submit(expirySide);
			Future<Boolean> paymentFuture = pool.submit(paymentSide);
			assertTrue(ready.await(10, TimeUnit.SECONDS), "both racers arrived in round " + round);
			// Snapshot before the latch opens: the drill is cumulative, so the only
			// meaningful assertion is on this round's delta.
			Map<String, Object> before = jdbc.queryForMap(
					"SELECT reserved, sold FROM stock_levels WHERE sku = ?", sku);
			int reservedBefore = num(before, "reserved");
			int soldBefore = num(before, "sold");
			start.countDown();

			String expiryResult = expiryFuture.get(30, TimeUnit.SECONDS);
			boolean paymentWon = paymentFuture.get(30, TimeUnit.SECONDS);

			// I5: one resolution per reservation, whatever the interleaving.
			assertEquals(1, intAt("SELECT COUNT(*) FROM reservation_resolution WHERE reservation_id = ?",
					reservationId), "round " + round + ": exactly one resolution");

			String outcome = jdbc.queryForObject(
					"SELECT outcome FROM reservation_resolution WHERE reservation_id = ?", String.class, reservationId);
			// The intended winner must actually win: this is what proves the gate
			// decided, rather than the drill's timing happening to land one way.
			assertEquals(paymentHeadStart ? "captured" : "expired", outcome,
					"round " + round + ": expected the " + (paymentHeadStart ? "payment" : "expiry")
							+ " to take the gate, got " + outcome);
			if (paymentWon) {
				captured.incrementAndGet();
			} else {
				expired.incrementAndGet();
			}
			// The loser's own report agrees with the row. LOST_RACE/ALREADY_RESOLVED
			// mean expiry was not the winner.
			assertEquals(paymentWon, !"EXPIRED".equals(expiryResult),
					"round " + round + ": expiry returned " + expiryResult + " but the gate says "
							+ (paymentWon ? "payment" : "expiry") + " won");

			// Winner effects are exact, measured as this round's delta.
			Map<String, Object> row = jdbc.queryForMap(
					"SELECT reserved, sold, total FROM stock_levels WHERE sku = ?", sku);
			int reserved = num(row, "reserved");
			int sold = num(row, "sold");
			int total = num(row, "total");
			if (paymentWon) {
				assertEquals(reservedBefore - 1, reserved, "round " + round + ": captured moved the unit out of reserved");
				assertEquals(soldBefore + 1, sold, "round " + round + ": captured moved the unit into sold");
			} else {
				assertEquals(reservedBefore - 1, reserved, "round " + round + ": expired returned the unit");
				assertEquals(soldBefore, sold, "round " + round + ": expired does not touch sold");
			}

			// Loser artefact. When the webhook loses, spec 6(d) requires the refund.
			int intents = intAt("SELECT COUNT(*) FROM refund_intent WHERE reservation_id = ?", reservationId);
			if (paymentWon) {
				assertEquals(0, intents, "round " + round + ": no refund when the payment wins");
			} else {
				assertEquals(1, intents, "round " + round + ": the losing webhook opens a refund");
				refunds.incrementAndGet();
			}

			// I1 at every step, not just at the end.
			assertTrue(reserved + sold <= total, "round " + round + ": I1 holds, got " + reserved + "+" + sold + ">" + total);
		}
		pool.shutdown();

		System.out.println("[D3] rounds=" + ROUNDS + " expired=" + expired.get() + " captured=" + captured.get()
				+ " refundIntentRows=" + refunds.get());

		// The drill is only evidence if BOTH branches were observed.
		assertEquals(ROUNDS, expired.get() + captured.get(), "every round resolved exactly once");
		assertTrue(expired.get() > 0 && captured.get() > 0,
				"BOTH outcomes must be observed, got expired=" + expired.get() + " captured=" + captured.get());
		assertEquals(expired.get(), refunds.get(), "one refund intent per webhook-loses round");

		// Final tally: every reserved unit either went back to stock or became sold.
		Map<String, Object> stock = jdbc.queryForMap(
				"SELECT reserved, sold, total FROM stock_levels WHERE sku = ?", sku);
		assertEquals(0, num(stock, "reserved"), "no units left dangling");
		assertEquals(captured.get(), num(stock, "sold"), "sold equals the number of payments that won");
	}

	// -- the payment side, stubbed until PaymentCaptureService exists ----------

	/**
	 * STANDS IN FOR PaymentCaptureService.captureForTest.
	 *
	 * @return true if this call won the gate and moved the units.
	 *
	 * Contract the real service must satisfy:
	 *  - gate first: INSERT INTO reservation_resolution with outcome 'captured'
	 *    and event_key {reservation_id}:captured. DuplicateKeyException means the
	 *    sweeper won.
	 *  - on losing, call refundForTest and return false. Touch nothing else.
	 *  - on winning, write the PaymentCaptured event, move BOTH counters in one
	 *    statement (reserved - q, sold + q), then stamp resolved_at expecting 1 row.
	 *
	 * Not @Transactional on purpose: one connection cannot be in two transactions,
	 * so a single transaction would serialise the two sides and remove the race
	 * being measured.
	 */
	private boolean captureAndSettle(String reservationId, String orderId, String sku, int quantity) {
		// Re-check first, mirroring expireOne's guard. Without this the payment is
		// structurally one statement ahead of expiry and wins every round, which
		// measures the drill's asymmetry rather than the design's race.
		// No expiry predicate: lateness is decided by the gate, not by a clock read.
		Integer live = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reservations WHERE reservation_id = ? AND resolved_at IS NULL",
				Integer.class, reservationId);
		if (live == 0) {
			// Already resolved, so there is no race to win. But who resolved it matters:
			//   expired  - expiry beat us. The money has still arrived for an order we can
			//              no longer fulfil, so the refund must be opened (spec 6(d)).
			//   captured - we already did this, i.e. a webhook redelivery. Idempotent no-op.
			// Returning silently here would be the bug the drill exists to catch: the
			// webhook-loses branch is reachable WITHOUT a primary-key conflict.
			String already = jdbc.queryForObject(
					"SELECT outcome FROM reservation_resolution WHERE reservation_id = ?",
					String.class, reservationId);
			if ("expired".equals(already)) {
				refundForTest(reservationId, orderId);
			}
			return false;
		}
		try {
			jdbc.update("INSERT INTO reservation_resolution (reservation_id, outcome, event_key) VALUES (?, 'captured', ?)",
					reservationId, reservationId + ":captured");
		} catch (org.springframework.dao.DuplicateKeyException e) {
			refundForTest(reservationId, orderId);
			return false;
		}
		jdbc.update("INSERT INTO events (account_id, idempotency_key, event_type, occurred_at, payload)"
				+ " VALUES (?, ?, 'PaymentCaptured', now(), CAST(? AS jsonb)) ON CONFLICT DO NOTHING",
				"acct-capture", reservationId + ":captured",
				"{\"reservation_id\":\"" + reservationId + "\"}");
		int moved = jdbc.update("UPDATE stock_levels SET reserved = reserved - ?, sold = sold + ? WHERE sku = ?"
				+ " AND reserved >= ?", quantity, quantity, sku, quantity);
		if (moved != 1) {
			throw new IllegalStateException("payment won the gate but could not move units: " + reservationId);
		}
		int stamped = jdbc.update("UPDATE reservations SET resolved_at = now() WHERE reservation_id = ?"
				+ " AND resolved_at IS NULL", reservationId);
		if (stamped != 1) {
			throw new IllegalStateException("payment won the gate but could not stamp resolved_at: " + reservationId);
		}
		return true;
	}

	/**
	 * STANDS IN FOR the spec 6(d) webhook-loses branch.
	 *
	 * The money consequence: we hold payment for an order we can no longer fulfil,
	 * so the refund must be recorded. Phase 4 replaces this with the real Stripe
	 * call; the intent row is the part that must not be skipped.
	 *
	 * ON CONFLICT DO NOTHING because webhooks redeliver.
	 */
	private void refundForTest(String reservationId, String orderId) {
		jdbc.update("INSERT INTO refund_intent (reservation_id, order_id, reason) VALUES (?, ?, ?)"
				+ " ON CONFLICT (reservation_id) DO NOTHING",
				reservationId, orderId, "payment_arrived_after_expiry");
	}

	// -- helpers --------------------------------------------------------------

	private ResponseEntity<String> postOrder(String account, String orderId, String sku, int quantity, String key) {
		Map<String, Object> body = Map.of(
				"account_id", account, "order_id", orderId, "sku", sku,
				"quantity", quantity, "idempotency_key", key);
		return rest.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.onStatus(status -> true, (request, res) -> {
				}).toEntity(String.class);
	}

	private void seedSku(String sku, int total, int ttlSeconds) {
		jdbc.update("INSERT INTO stock_levels (sku, total, reserved, sold, reservation_ttl_seconds)"
				+ " VALUES (?, ?, 0, 0, ?) ON CONFLICT (sku) DO NOTHING", sku, total, ttlSeconds);
	}

	private int intAt(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private static int num(Map<String, Object> row, String column) {
		return ((Number) row.get(column)).intValue();
	}

	private static String shortId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
