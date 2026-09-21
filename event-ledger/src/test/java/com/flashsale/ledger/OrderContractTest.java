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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// The frozen POST /orders response contract (plan section 0.1).
// ReservationApiTest asserts status codes only, which is how the SOLD_OUT body
// drifted from the literal `null` to an empty body without a red suite. These
// tests assert the bodies themselves.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderContractTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ObjectMapper mapper;

	private RestClient rest;

	@BeforeEach
	void setUpClient() {
		rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	// 0.1: RESERVED is 201 with the six-field reservation JSON.
	@Test
	void reservedReturns201WithSixFieldReservation() {
		String sku = "TEST-C1-" + shortId();
		seedSku(sku, 5, 900);
		String account = "acct-c1-" + shortId();
		String key = "key-c1-" + shortId();

		ResponseEntity<String> res = postOrder(account, "ord-" + key, sku, 1, key);

		assertEquals(201, res.getStatusCode().value());
		JsonNode body = parse(res.getBody());
		assertEquals(List.of("account_id", "expires_at", "order_id", "quantity", "reservation_id", "sku"),
				fieldNames(body), "exactly the six frozen fields; a message field here breaks replay equality");
		assertTrue(body.get("expires_at").asText().matches("\\d{4}-\\d{2}-\\d{2}T.*Z"),
				"expires_at is ISO-8601 UTC, got: " + body.get("expires_at").asText());
	}

	// 0.1 and gate P1: DUPLICATE is 200 with a body byte-identical to the first 201.
	// This assertion is the idempotency contract, so it must hold on the raw string,
	// not on a parsed comparison.
	@Test
	void duplicateReturns200WithByteIdenticalBody() {
		String sku = "TEST-C2-" + shortId();
		seedSku(sku, 5, 900);
		String account = "acct-c2-" + shortId();
		String key = "key-c2-" + shortId();

		ResponseEntity<String> first = postOrder(account, "ord-" + key, sku, 1, key);
		assertEquals(201, first.getStatusCode().value());

		ResponseEntity<String> retry = postOrder(account, "ord-" + key, sku, 1, key);
		assertEquals(200, retry.getStatusCode().value());
		assertEquals(first.getBody(), retry.getBody(),
				"the replay guarantee is byte equality, so no per-request field may enter the body");
	}

	// 0.1: SOLD_OUT is 409 with the body literal `null`. A JSON client calling
	// JSON.parse on it must get null; an empty body is a parse error instead.
	@Test
	void soldOutReturns409WithLiteralNullBody() {
		String sku = "TEST-C3-" + shortId();
		seedSku(sku, 2, 900);
		String account = "acct-c3-" + shortId();
		String key = "key-c3-" + shortId();

		ResponseEntity<String> res = postOrder(account, "ord-" + key, sku, 3, key);

		assertEquals(409, res.getStatusCode().value());
		assertEquals("null", res.getBody(), "frozen body is the four-character literal");
		assertTrue(parse(res.getBody()).isNull(), "parses as JSON null");
	}

	// New outcome, and the reason it needs its own shape: both this and SOLD_OUT
	// are 409, so the body is the only thing a client can branch on.
	@Test
	void orderAlreadyReservedReturns409WithProblemDetail() {
		String sku = "TEST-C4-" + shortId();
		seedSku(sku, 5, 900);
		String account = "acct-c4-" + shortId();
		String orderId = "ord-" + shortId();

		assertEquals(201, postOrder(account, orderId, sku, 1, "key-a-" + shortId()).getStatusCode().value());
		ResponseEntity<String> res = postOrder(account, orderId, sku, 1, "key-b-" + shortId());

		assertEquals(409, res.getStatusCode().value());
		JsonNode body = parse(res.getBody());
		assertEquals(409, body.get("status").asInt());
		assertEquals(orderId, body.get("order_id").asText(), "names the conflicting order");
		assertTrue(body.get("detail").asText().contains("idempotency key"),
				"the detail names the remedy, got: " + body.get("detail").asText());

		// The distinction is useless if the two are not actually different.
		ResponseEntity<String> soldOut = postOrder(account, "ord-x-" + shortId(), sku, 99, "key-x-" + shortId());
		assertEquals(409, soldOut.getStatusCode().value());
		assertTrue(!res.getBody().equals(soldOut.getBody()), "the two 409 bodies differ");

		// And nothing leaked: no statement, table name, or constraint may appear.
		assertTrue(!res.getBody().contains("INSERT INTO") && !res.getBody().contains("constraint"),
				"no SQL internals in a client-facing body");
		assertEquals(1, intAt("SELECT reserved FROM stock_levels WHERE sku = ?", sku),
				"the rejected attempt left its increment rolled back");
	}

	private JsonNode parse(String body) {
		return mapper.readTree(body);
	}

	private List<String> fieldNames(JsonNode node) {
		return node.propertyNames().stream().sorted().toList();
	}

	private ResponseEntity<String> postOrder(String account, String orderId, String sku, int quantity, String key) {
		Map<String, Object> body = Map.of(
				"account_id", account, "order_id", orderId, "sku", sku,
				"quantity", quantity, "idempotency_key", key);
		ResponseEntity<String> response = rest.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.onStatus(status -> true, (request, res) -> {
				}).toEntity(String.class);
		assertNotNull(response.getBody(), "every outcome returns a body, not a transport failure");
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
