# Booking Service

Spring Boot service that owns ticket inventory, reservations, hold expiry,
idempotency, Redis admission control, and the transactional outbox.

The staged learning plan is in [ROADMAP.md](./ROADMAP.md).

## Run and test

Requirements: Java 21. From this repository's root:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The default profile expects PostgreSQL at `localhost:5432`. Redis and Kafka
integrations are disabled by default; enable them when those dependencies are
available. For a database-only H2 run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

The service listens on port `8081`. Provide its infrastructure independently;
this repository does not rely on a root Maven aggregator or shared Compose
project.

## API

- `POST /api/events/{eventId}/reservations`
- `GET /api/reservations/{reservationId}`
- `POST /api/reservations/{reservationId}/confirm`
- `POST /api/reservations/{reservationId}/cancel`
- Internal inventory initialization and availability endpoints live under
  `/internal/inventory`; they are used by the catalog service.

An optional `Idempotency-Key` header makes retried hold requests safe. The
inventory contract and reservation DTOs are defined locally under
`com.mahesh.flashsale.api`. Keep those JSON fields compatible with the catalog
client and notification event contract.

## Build a container

```powershell
docker build -t flashsale-booking .
```

This folder is a standalone repository. From its root, the Maven Wrapper can
build and test it without a parent project or sibling service folders.

## Learning sequence

This service already contains PostgreSQL/Flyway, both JPA and JDBC inventory
implementations, optional Redis admission control, and the Kafka transactional
outbox. Work through them in order; the presence of later-stage code does not
mean its failure behavior has been proven.

1. **PostgreSQL and JDBC vs JPA:** run the Testcontainers/PostgreSQL test when
   Docker is available. Compare both conditional inventory updates and preserve
   the rule that successful holds never exceed inventory.
2. **Idempotency and transactions:** retry a hold with the same
   `Idempotency-Key`; verify exactly one reservation and one inventory change.
3. **Redis admission:** measure the per-email rate limit, stop Redis, and verify
   booking degrades without changing PostgreSQL inventory correctness.
4. **Kafka outbox:** verify each reservation state change writes an outbox row
   in its database transaction, then test retries and duplicate publication.
5. **Service boundary:** keep booking as the only owner allowed to decrement
   ticket inventory; catalog calls its inventory API.
6. **AWS:** deploy only after local PostgreSQL/Redis/Kafka behavior is tested;
   set a budget and tear down resources after practice.
7. **Load/failure drills:** send a burst larger than inventory and verify
   successes never exceed stock; restart dependencies and check recovery.
