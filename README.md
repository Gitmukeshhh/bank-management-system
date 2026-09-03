# BankMS — Bank Management System

A backend banking system built the way a real core-banking / payments team would build
it — not a CRUD demo where a balance is just a number that goes up or down.

**The three things that make this different from a typical portfolio banking project:**

1. **Double-entry ledger accounting.** Every money movement writes two linked ledger rows
   (a debit and a credit), not just a balance update. The books always reconcile to zero.
2. **Concurrency-safe fund transfers.** Two requests hitting the same account at the same
   instant are serialized with a pessimistic row lock, taken in a deadlock-safe order
   (lower account id first) for transfers between two accounts. This is proven, not just
   claimed — see the concurrency test below.
3. **Idempotent transactions + full audit trail.** Every deposit/withdrawal/transfer
   carries a client-supplied idempotency key, so a retried request never double-executes.
   Every action (financial or administrative) writes a permanent, admin-only audit log entry.

On top of that: role-based access (`ADMIN` / `TELLER` / `CUSTOMER`) with account
ownership enforcement, minimum-balance / overdraft rules per account type, daily transfer
limits, account freeze/close lifecycle, and a scheduled job that accrues daily interest on
savings accounts.

A plain-language, diagram-led explanation of how it all works (no code) is here:
the `bankms-explained` artifact published alongside this project.

## Tech stack

- Java 17, Spring Boot 3.3 (Web, Data JPA, Security, Validation)
- H2 (in-memory — swap for PostgreSQL in production; see below)
- JWT auth (`jjwt`), method-level `@PreAuthorize` for role checks
- Pessimistic locking (`SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)`) +
  optimistic `@Version` as a secondary safety net
- `@Scheduled` interest accrual job
- Lombok, JUnit 5 + AssertJ, real multi-threaded concurrency tests

## Running it locally

```bash
mvn spring-boot:run
```

Runs on `http://localhost:8080`. Seeded users: `admin / admin123` (ADMIN),
`teller1 / teller123` (TELLER). A branch (`Head Office`, IFSC `BANK0000001`) is
seeded too. H2 console: `http://localhost:8080/h2-console`.

```bash
mvn test
```

4 tests, including a real multi-threaded test (`ConcurrentWithdrawalTest`) that fires two
simultaneous withdrawals for the account's full balance and asserts exactly one succeeds
and the final balance is correct — the classic banking double-spend bug, proven fixed.

## Running it in Docker

```bash
docker build -t bankms .
docker run -p 8080:8080 bankms
```

## Deploying it live (Render)

Vercel doesn't run JVM/Spring Boot apps (its runtimes are Node/Python/Go/Edge only) — this
repo ships a `Dockerfile` and `render.yaml` for **Render**, which runs Docker web services
on a free tier:

1. Push this repo to GitHub (already done if you're reading this on GitHub).
2. Go to [render.com](https://render.com) → New → **Blueprint** → connect this repo.
   Render reads `render.yaml`, builds the `Dockerfile`, and generates a secure `JWT_SECRET`
   automatically.
3. First build takes a few minutes; after that you get a public URL serving the API.

Note: this demo runs on H2 in-memory storage, so data resets on every redeploy/restart —
intentional for a portfolio demo. For a persistent deployment, add a managed Postgres
instance and point `spring.datasource.*` at it.

## API walkthrough

**1. Log in (seeded users) or register a customer**
```bash
curl -X POST $BASE/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

curl -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{
  "username":"ravi1","password":"pass123","fullName":"Ravi Kumar","dob":"1990-05-14",
  "panNumber":"ABCDE1234F","phone":"9876543210","email":"ravi@example.com"
}'
```

**2. Admin/Teller opens an account for a customer**
```bash
curl -X POST $BASE/api/accounts -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" -d '{
    "customerUsername":"ravi1","branchId":1,"accountType":"SAVINGS","initialDeposit":5000,
    "minBalance":500,"interestRatePercent":4,"dailyTransferLimit":50000
  }'
```

**3. Customer self-service: deposit / withdraw / transfer**
```bash
curl -X POST $BASE/api/transactions/withdraw -H "Authorization: Bearer <customer-token>" \
  -H "Content-Type: application/json" -d '{
    "accountNumber":"<accountNumber>","amount":1000,"idempotencyKey":"w-001"
  }'
```
Retry the exact same request (same `idempotencyKey`) — you get the same recorded
transaction back, not a second debit.

**4. Statement, freeze, audit log**
```bash
curl "$BASE/api/accounts/<accountNumber>/statement" -H "Authorization: Bearer <token>"
curl -X PATCH "$BASE/api/accounts/<accountNumber>/status?status=FROZEN" -H "Authorization: Bearer <admin-token>"
curl "$BASE/api/audit-logs" -H "Authorization: Bearer <admin-token>"
```

## Design notes worth knowing before you extend it

- **Never load an `Account` unlocked and then lock it again in the same transaction.**
  `AccountRepository.findIdByAccountNumber` returns a scalar id specifically so the entity
  is never put into the persistence context unlocked — doing so poisons Hibernate's
  `@Version` check and makes the subsequent pessimistic-lock fetch throw spuriously.
- **`spring.jpa.open-in-view=false`.** Every service method that returns data touching a
  lazy association (`Branch`, `Customer`) is explicitly `@Transactional(readOnly = true)`
  — relying on Open Session In View to paper over this is avoided on purpose.
- **Interest posting is a separate bean from the scheduler**, because a same-class method
  call bypasses Spring's transactional proxy (the classic self-invocation pitfall).

## What I'd add next

- PostgreSQL + Flyway migrations instead of H2 + `ddl-auto=update`.
- A manual "post interest now" admin endpoint for demo purposes (currently only runs on cron).
- Refresh tokens / token revocation.
- Rate limiting on the transaction endpoints.
- Multi-currency support.
