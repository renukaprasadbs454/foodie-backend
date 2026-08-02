# foodie-backend

Modular monolith backend for the Foodie food-delivery platform.

**Stack:** Java 21 · Spring Boot 3 · Spring Security · Spring Data JPA · PostgreSQL · Redis · Flyway · WebSocket (STOMP) · Docker · Maven

**Specification:** `Docs/Phase3_Backend_Architecture.md` (v1.1) · `Docs/04_API_Contracts.md` (v1.1) + `Docs/04_API_Contracts.md.docx` (v1.0)

## Current status

**Phase A scaffold** tagged `v0.1.0-scaffold`.

**Module 1 — Authentication** tagged `v0.2.0-auth`.

**Module 2 — User (Customer)** tagged `v0.3.0-user`.

**Module 3 — Restaurant** tagged `v0.4.0-restaurant`.

**Module 4 — Menu** tagged `v0.5.0-menu`.

**Module 5 — Cart** tagged `v0.6.0-cart` (frozen).

**Module 6 — Order** tagged `v0.7.0-order` (frozen).

**Module 7 — Payment** tagged `v0.8.0-payment` (frozen).

**Module 8 — Delivery** implemented (pending review):

- Delivery partner profile, KYC document upload, availability (online requires VERIFIED KYC)
- Assignment offers, accept (optimistic lock), pickup/delivery OTP verification
- Redis GEO live location pings + WebSocket broadcast on `/topic/order/{orderId}`
- Publishes `DeliveryPartnerAssignedEvent`, `DeliveryCompletedEvent`, `DeliveryLocationUpdatedEvent`
- Consumes `OrderStatusChangedEvent` (READY_FOR_PICKUP) to create assignments
- Flyway: `V9__delivery.sql` (`delivery_partner`, `delivery_partner_document`, `delivery_assignment`)
- Redis: `geo:partners`, `partner:{id}:lastSeen`, `ratelimit:location:{partnerId}`

**Not yet implemented:** Wallet, Notification, Review, Coupon, Admin, Analytics.

## Prerequisites

- JDK 21+
- Docker Desktop (for local Postgres/Redis and Testcontainers)
- Maven Wrapper (`./mvnw` / `mvnw.cmd`) — no global Maven required

## Project setup

```bash
# Clone / enter backend repo
cd foodie-backend

# Optional: local env overrides (never commit secrets)
cp .env.example .env

# Start infrastructure only
docker compose up -d postgres redis

# Confirm infra
docker compose ps
docker exec foodie-postgres pg_isready -U foodie -d foodie
docker exec foodie-redis redis-cli ping

# Build
./mvnw -B -DskipTests package

# Run tests (unit + Testcontainers integration; Docker required for IT)
./mvnw -B test

# Run API (Flyway baseline runs automatically on startup)
./mvnw spring-boot:run

# Verify
curl http://localhost:8080/actuator/health
# OpenAPI UI: http://localhost:8080/swagger-ui.html
```

Windows (Git Bash / PowerShell): use `./mvnw.cmd` if `./mvnw` is unavailable.

Full stack (build + run backend container):

```bash
docker compose up --build
```

Nginx edge proxy (optional profile):

```bash
docker compose --profile full up --build
```

### Flyway

- Migrations live in `src/main/resources/db/migration/`
- `V1__baseline.sql` — empty baseline
- `V2__auth.sql` — Module 1
- `V3__user.sql` — Module 2
- `V4__restaurant.sql` — Module 3
- `V5__menu.sql` — Module 4
- `V6__cart.sql` — Module 5
- `V7__order.sql` — Module 6
- `V8__payment.sql` — Module 7
- `V9__delivery.sql` — Module 8
- Applied automatically on application startup when Postgres is reachable
- Verify: `docker exec foodie-postgres psql -U foodie -d foodie -c "SELECT * FROM flyway_schema_history;"`

## Profiles

| Profile | Purpose |
|---|---|
| `local` (default) | Developer machine; Docker Compose Postgres/Redis |
| `dev` | Shared development environment |
| `staging` | Pre-production |
| `prod` | Production (JSON logs, secrets via env) |

Set via `SPRING_PROFILES_ACTIVE`. Secrets are never committed — see `.env.example`.

## Package layout

```
com.foodie
├── FoodieApplication
├── common / shared / config / security / infrastructure
├── auth | user | restaurant | menu | cart | order | payment
├── delivery | wallet | notification | review | coupon | admin | analytics
└── realtime
```

See Phase 3 §1 for ownership and dependency rules.

## Tests

```bash
# Unit smoke (no Docker)
./mvnw -Dtest=FoodieApplicationTests test

# Integration (requires Docker)
./mvnw test
```

Integration tests extend `com.foodie.support.AbstractIntegrationTest` (Postgres + Redis Testcontainers).

## CI

GitHub Actions workflow: `.github/workflows/ci.yml` — Java 21, `./mvnw package` + `./mvnw test` on PR/push to `main`/`develop`.

## Next

Module 1 — Authentication (after Phase A review approval).
