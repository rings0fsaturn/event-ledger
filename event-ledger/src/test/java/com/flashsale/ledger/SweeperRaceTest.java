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

import com.flashsale.ledger.payment.PaymentCaptureOutcome;
import com.flashsale.ledger.payment.PaymentCaptureService;
import com.flashsale.ledger.sweep.ReservationExpiryService;

// D3 drill (spec 6(d), 8b.6 D3). Both writers are real beans now:
// expiry via ReservationExpiryService, payment via PaymentCaptureService.
// The gate (reservation_resolution PK) decides the winner, not the latch timing.
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

	@Autowired
	PaymentCaptureService paymentCaptureService;

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
				return paymentCaptureService.capture(reservationId, orderId)
						== com.flashsale.ledger.payment.PaymentCaptureOutcome.CAPTURED;
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

	// Stripe redelivery: two webhooks for the same paid order race each other.
	// Exactly one takes the gate, the loser re-reads `captured` and does nothing.
	// No refund in either case - the unit is sold, not lost.
	@Test
	void duplicateWebhookResolvesToOneCaptureAndNoRefund() throws Exception {
		String sku = "TEST-D2-" + shortId();
		seedSku(sku, 100, 900);

		String orderId = "ord-d2-" + shortId();
		String key = "key-d2-" + shortId();
		assertEquals(201, postOrder("acct-d2-" + shortId(), orderId, sku, 1, key).getStatusCode().value(),
				"reserves");
		String reservationId = jdbc.queryForObject(
				"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		Callable<PaymentCaptureOutcome> webhook = () -> {
			ready.countDown();
			assertTrue(start.await(10, TimeUnit.SECONDS), "both webhooks released");
			return paymentCaptureService.capture(reservationId, orderId);
		};

		Future<PaymentCaptureOutcome> first = pool.submit(webhook);
		Future<PaymentCaptureOutcome> second = pool.submit(webhook);
		assertTrue(ready.await(10, TimeUnit.SECONDS), "both webhooks arrived");
		start.countDown();

		PaymentCaptureOutcome outcomeA = first.get(30, TimeUnit.SECONDS);
		PaymentCaptureOutcome outcomeB = second.get(30, TimeUnit.SECONDS);
		pool.shutdown();

		// Gate: exactly one resolution, and it is the capture.
		assertEquals(1, intAt("SELECT COUNT(*) FROM reservation_resolution WHERE reservation_id = ?",
				reservationId), "exactly one resolution");
		assertEquals("captured", jdbc.queryForObject(
				"SELECT outcome FROM reservation_resolution WHERE reservation_id = ?", String.class, reservationId),
				"the gate says captured");

		// One CAPTURED, one NOT_CAPTURABLE, in either order.
		assertTrue(
				(outcomeA == PaymentCaptureOutcome.CAPTURED && outcomeB == PaymentCaptureOutcome.NOT_CAPTURABLE)
						|| (outcomeA == PaymentCaptureOutcome.NOT_CAPTURABLE && outcomeB == PaymentCaptureOutcome.CAPTURED),
				"one webhook captures, the redelivery is a no-op, got " + outcomeA + " and " + outcomeB);

		// Money: the unit moved once, and no refund exists because nothing was lost.
		Map<String, Object> stock = jdbc.queryForMap(
				"SELECT reserved, sold, total FROM stock_levels WHERE sku = ?", sku);
		assertEquals(0, num(stock, "reserved"), "unit left reserved");
		assertEquals(1, num(stock, "sold"), "unit moved to sold exactly once");
		assertEquals(0, intAt("SELECT COUNT(*) FROM refund_intent WHERE reservation_id = ?", reservationId),
				"no refund when the payment itself wins");
		assertTrue(num(stock, "reserved") + num(stock, "sold") <= num(stock, "total"), "I1 holds");
	}

	// Gate-contention door, forced deterministically: pre-insert the winner's
	// gate row, leave resolved_at NULL so the re-check still finds the row live,
	// then call capture. The gate insert must hit the PK and take the catch path.
	// Case 1: winner is `captured` (redelivery racing itself).
	@Test
	void gateLoserToCapturedReturnsNotCapturableWithoutRefund() {
		String sku = "TEST-GATE-C-" + shortId();
		seedSku(sku, 100, 900);

		String orderId = "ord-gate-c-" + shortId();
		String key = "key-gate-c-" + shortId();
		assertEquals(201, postOrder("acct-gate-c-" + shortId(), orderId, sku, 1, key).getStatusCode().value(),
				"reserves");
		String reservationId = jdbc.queryForObject(
				"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);

		// Simulate the winner's commit: gate row present, stamp not yet visible.
		jdbc.update("INSERT INTO reservation_resolution (reservation_id, outcome, event_key) VALUES (?, 'captured', ?)",
				reservationId, reservationId + ":captured");

		PaymentCaptureOutcome outcome = paymentCaptureService.capture(reservationId, orderId);

		assertEquals(PaymentCaptureOutcome.NOT_CAPTURABLE, outcome, "loser to captured is a no-op");
		assertEquals(1, intAt("SELECT COUNT(*) FROM reservation_resolution WHERE reservation_id = ?",
				reservationId), "still one resolution");
		assertEquals("captured", jdbc.queryForObject(
				"SELECT outcome FROM reservation_resolution WHERE reservation_id = ?", String.class, reservationId),
				"winner stays captured");
		assertEquals(0, intAt("SELECT COUNT(*) FROM refund_intent WHERE reservation_id = ?", reservationId),
				"no refund when the other capture won");
		assertEquals(1, intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku),
				"stock untouched by the loser");
		assertEquals(0, intAt("SELECT sold FROM stock_levels WHERE sku = ?", sku), "nothing sold twice");
	}

	// Case 2: winner is `expired` (entry point 1 - lost the gate to the sweeper).
	@Test
	void gateLoserToExpiredOpensRefund() {
		String sku = "TEST-GATE-E-" + shortId();
		seedSku(sku, 100, 900);

		String orderId = "ord-gate-e-" + shortId();
		String key = "key-gate-e-" + shortId();
		assertEquals(201, postOrder("acct-gate-e-" + shortId(), orderId, sku, 1, key).getStatusCode().value(),
				"reserves");
		String reservationId = jdbc.queryForObject(
				"SELECT reservation_id FROM reservations WHERE order_id = ?", String.class, orderId);

		// Simulate expiry's commit winning the gate just before our insert.
		jdbc.update("INSERT INTO reservation_resolution (reservation_id, outcome, event_key) VALUES (?, 'expired', ?)",
				reservationId, reservationId + ":expired");

		PaymentCaptureOutcome outcome = paymentCaptureService.capture(reservationId, orderId);

		assertEquals(PaymentCaptureOutcome.REFUND_OPENED, outcome, "loser to expiry opens a refund");
		assertEquals(1, intAt("SELECT COUNT(*) FROM reservation_resolution WHERE reservation_id = ?",
				reservationId), "still one resolution");
		assertEquals(1, intAt("SELECT COUNT(*) FROM refund_intent WHERE reservation_id = ?", reservationId),
				"one refund intent for the gate loss");
		assertEquals("payment_arrived_after_expiry", jdbc.queryForObject(
				"SELECT reason FROM refund_intent WHERE reservation_id = ?", String.class, reservationId),
				"frozen refund reason");
		assertEquals(1, intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku),
				"stock untouched by the loser");
		assertEquals(0, intAt("SELECT sold FROM stock_levels WHERE sku = ?", sku), "nothing sold");
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
