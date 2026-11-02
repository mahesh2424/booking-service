# Flash-Sale Ticketing: Build-and-Learn Roadmap

This is the learning plan for the three standalone repositories:

- `catalog-service`
- `booking-service` (this repository)
- `notification-service`

The repositories are already split. The original curriculum planned that split
for Stage 5, so the current code has some later-stage work in place early. We
will not rewrite it or add more infrastructure all at once. We will verify one
stage at a time, keeping this rule at the center:

> Successful ticket holds must never exceed the inventory in PostgreSQL.

Each stage should have a focused concept, a small change or verification, tests
you can run, and a clear completion check. Do not start the next stage until the
current check passes.

## Current state

- Java 21 and Spring Boot 4 services are in separate folders, each with its own
  Maven Wrapper and POM.
- `booking-service` owns inventory and reservations. It has JPA and JDBC
  inventory implementations, conditional inventory updates, hold expiry,
  idempotency, and a transactional outbox.
- `catalog-service` owns event details and calls booking to seed inventory. It
  has optional Redis caching and a Kafka cache-invalidation listener.
- `notification-service` has a Kafka consumer and an idempotent processed
  message table. Delivery is logged/simulated; no real email is sent.
- Redis and Kafka code and dependencies exist, but are disabled by default in
  local service configuration.
- Each service's tests pass independently. The PostgreSQL Testcontainers test
  is skipped when Docker is unavailable, so PostgreSQL concurrency behavior
  still needs a successful run with Docker.
- There is no shared/root Maven project or Compose environment. This is
  intentional for independently publishing the service repositories.

## Order of work

```text
Stage 2: PostgreSQL + JDBC/JPA proof
                |
                v
Stage 3: Redis cache and admission
                |
                v
Stage 4: Kafka outbox and consumer recovery
                |
                v
Stage 5: Service contracts and ownership
                |
                v
Stage 6: AWS deployment
                |
                v
Stage 7: Load and failure drills
```

The service split already exists; Stage 5 now means proving the boundaries,
contracts, and data ownership rather than extracting the services again.

## Stage 2 — PostgreSQL, JDBC, and JPA

**Learn:** JDBC versus JPA, the shared `DataSource`, connection pooling, prepared
statements, Spring transaction boundaries, Flyway migrations, and the difference
between PostgreSQL and H2 locking/update behavior.

**Current implementation:** PostgreSQL and Flyway configuration, both
conditional-update inventory implementations, and JPA/JDBC flow tests exist.
There is a PostgreSQL Testcontainers test in `booking-service`; it needs Docker
to run. No root Compose project is provided.

**Next work:**

1. Install/start Docker Desktop and run the PostgreSQL Testcontainers test in
   `booking-service`.
2. Verify Flyway creates the booking schema and Hibernate validates it without
   applying automatic schema updates.
3. Run the same inventory behavior for both configured backends:
   `ticketing.inventory.backend=jpa` and `ticketing.inventory.backend=jdbc`.
4. Confirm concurrent holds never oversell and that an idempotent retry changes
   inventory only once.
5. Add or fix only the PostgreSQL-specific issues found by these checks.
6. Document how to run the test and record the result.

**Important connection detail:** both JDBC and JPA must use the same Spring
`DataSource` and transaction boundary. A JDBC inventory update inside a JPA
transaction can commit or roll back with the reservation; it is not a second
database transaction.

**Success check:** the PostgreSQL concurrency test passes for both inventory
backends, and you can explain why
`UPDATE ... WHERE available_tickets >= :quantity` is safer than reading the
quantity into Java and updating it later.

Do not begin Redis behavior experiments until this check passes.

## Stage 3 — Redis: cache and admission, not inventory authority

**Learn:** Redis keys, TTLs, cache-aside, invalidation, cache stampedes, and
rate limiting.

Redis integrations already exist, but remain optional. Verify and learn them in
this order:

1. Run Redis locally and confirm service connections.
2. Measure a catalog event cache miss followed by a hit.
3. Confirm booking lifecycle events invalidate the cached event view.
4. Measure booking admission/rate-limit behavior.
5. Stop Redis and confirm requests degrade gracefully without corrupting
   PostgreSQL inventory.

**Rules:** PostgreSQL remains the durable inventory source of truth. Do not move
`available_tickets` exclusively into Redis, and do not add a Redis/PostgreSQL
dual-write for inventory without a tested reconciliation design.

**Success check:** cache hits/misses and rate limits are observable; losing
Redis does not create oversold or lost bookings.

## Stage 4 — Kafka and the transactional outbox

**Learn:** topics, keys, consumer groups, at-least-once delivery, duplicate
messages, idempotency, retries, and dead-letter handling.

The booking outbox, relay, notification consumer, and dead-letter configuration
already exist. Verify them before expanding:

1. Confirm `ReservationHeld`, `ReservationConfirmed`, `ReservationCancelled`,
   and `ReservationExpired` outbox rows are committed in the same PostgreSQL
   transaction as their reservation changes.
2. Start Kafka locally and verify the relay publishes outbox rows.
3. Restart the consumer and replay/duplicate a message; verify
   `messageId`-based processing prevents duplicate notification work.
4. Send a malformed event and verify bounded retries and dead-letter routing.
5. Stop the notification consumer, create events, restart it, and verify it
   catches up without changing booking inventory.

Use the event ID as the Kafka key when ordering by concert is the desired
behavior; use reservation ID when per-reservation ordering is the requirement.
Choose and document one contract before depending on it.

**Success check:** a committed reservation change always has its outbox record,
and consumer retries/restarts cannot release inventory or repeat notification
work.

## Stage 5 — Service ownership and contracts

The folders already exist as separate repositories. Focus on the boundaries:

- **Catalog:** owns event metadata and sale windows.
- **Booking:** owns ticket availability, reservations, and the only operation
  that decrements inventory.
- **Notification:** consumes booking events and records/simulates delivery.

The catalog-to-booking inventory HTTP contract and booking-to-consumer Kafka
event types are currently duplicated locally so each repository builds alone.
Keep their JSON field names, types, and semantics compatible. Add contract
tests or versioned schemas before changing either side.

**Success check:** catalog cannot directly modify booking data, notification
cannot modify ticket inventory, and each service can be built/tested without
sibling folders.

## Stage 6 — AWS, after local dependencies work

**Learn:** containers, ECS/Fargate, RDS PostgreSQL, ElastiCache Redis, secrets,
health checks, and cloud logs.

1. Build and run each service container independently.
2. Set an AWS Budget and alert before creating resources.
3. Start with a small ECS/Fargate deployment and RDS PostgreSQL.
4. Add ElastiCache only if the verified Stage 3 design needs it.
5. Keep Kafka local initially. Evaluate a managed Kafka service only when its
   cost and operations are part of the learning goal.
6. Use Secrets Manager or SSM for credentials; never bake them into images.
7. Tear down resources after practice.

**Success check:** health checks, logs, configuration, and secret injection work
without committing credentials or leaving expensive resources running.

## Stage 7 — Load and failure drills

**Learn:** throughput versus correctness, failure isolation, and recovery.

Run a burst of `N` one-ticket requests against inventory `M`; successful holds
must be at most `M`. Then exercise:

- Redis stopped during reads and holds.
- Kafka consumer stopped and restarted.
- Duplicate outbox publication.
- Delayed notification processing.
- Booking application restart during a sale.

**Authority rules:** PostgreSQL wins for ticket inventory and reservations;
Redis is cache/admission; Kafka is integration/notification transport. Rebuild
or invalidate caches from authoritative data rather than treating cache values
as inventory truth.

## Learning loop for every stage

1. Explain the concept in terms of this booking flow.
2. Make one small code/config change or run one focused verification.
3. Run the relevant tests and inspect the behavior.
4. Recheck that successful holds never exceed inventory.
5. Note one production failure mode and how this design responds.

## Out of scope for now

Authentication, real payments, real email delivery, a full storefront,
production security hardening, Kubernetes, and always-on managed Kafka are not
required to complete these learning stages.
