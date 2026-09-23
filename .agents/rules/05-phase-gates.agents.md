# Phase Gates

- Follow the defined phase order; do not merge phases.
- Do not begin Phase N+1 until the author can explain Phase N and its key decisions.
- Current sequence:
  1. Schema + idempotent write
  2. Reservation + `FOR UPDATE` guard + TTL sweeper
  3. Kafka producer/consumer + post-transaction offset commit
  4. Stripe test charge + verified webhook
  5. Replay + snapshot + determinism
  6. Chaos drills under load
  7. Observability + load test + measured numbers
- Treat the lock path and sweeper race as early correctness gates before dependent work.
