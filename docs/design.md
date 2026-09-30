# ReceiptTrust MVP — Design

## Overview

ReceiptTrust is a backend-only REST API. It is a layered Spring Boot application:

```
Controller (REST/DTO)  ->  Service (business logic, tx)  ->  Repository (JPA)  ->  PostgreSQL
                                     |
                              Storage (local FS for images)
```

Cross-cutting concerns: JWT authentication filter, global exception handler, bean validation,
Flyway migrations.

### Technology Stack

| Concern            | Choice                                   |
|--------------------|------------------------------------------|
| Language           | Java 21                                  |
| Framework          | Spring Boot 3.3.x                        |
| Security           | Spring Security 6 + JJWT (JWT)           |
| Persistence        | Spring Data JPA (Hibernate)              |
| Database           | PostgreSQL 16                            |
| Migrations         | Flyway                                   |
| Build              | Maven                                    |
| Image storage      | Local filesystem (configurable path)     |
| Money              | `java.math.BigDecimal`, scale 2          |
| Testing            | JUnit 5, Spring Boot Test, H2 for slices |

### Package Structure

```
com.receipttrust
├── config          // security, web, storage config
├── security        // JWT filter, token service, user details
├── common          // error handling, base entities, pagination
├── user            // user, profile
├── friend          // friend requests / friendships
├── receipt         // receipts + items + storage
├── assignment      // item assignments
├── debt            // debts, generation, dashboard, explanation
├── payment         // payments, approval, history
├── trust           // trust score engine + audit
└── notification    // in-app notifications
```

---

## Data Model

Entities and key columns. All tables have `id` (BIGINT identity), `created_at`, `updated_at`.
Money columns are `NUMERIC(12,2)`.

### users
- full_name, username (unique), email (unique), password_hash
- profile_image_path (nullable)
- trust_score (int, default 500)

### refresh_tokens
- user_id (FK), token (unique), expires_at, revoked (bool)

### friendships
- requester_id (FK users), addressee_id (FK users)
- status: PENDING | ACCEPTED | REJECTED
- unique constraint on (requester_id, addressee_id)

### receipts
- owner_id (FK users), store_name, purchase_date (date), notes (nullable)
- image_path, image_content_type
- finalized (bool, default false)

### receipt_items
- receipt_id (FK), name, quantity (int), unit_price (numeric)
- line_total is derived (quantity × unit_price), persisted for convenience

### item_assignments
- receipt_item_id (FK), assignee_id (FK users)
- split_type: INDIVIDUAL | EQUAL
- share_amount (numeric)  // resolved amount this assignee owes for this item

### debts
- creditor_id (FK users), debtor_id (FK users)
- receipt_id (FK), purchase_date (date)
- original_amount (numeric), outstanding_amount (numeric)
- status: ACTIVE | SETTLED
- due_date (date)                 // purchase_date + configurable term (default 14 days)
- settled_at (timestamp, nullable)

### debt_items  (snapshot of what makes up a debt — for the explanation page)
- debt_id (FK), item_name, amount (numeric)

### payments
- debt_id (FK), submitter_id (FK users)
- amount (numeric), method: CASH|BANK_TRANSFER|GCASH|MAYA|OTHER, notes (nullable)
- status: PENDING | APPROVED | REJECTED
- decided_at (timestamp, nullable)

### trust_score_events
- user_id (FK), delta (int), reason (string), resulting_score (int), created_at

### notifications
- user_id (FK), type (enum), message, read (bool, default false)

### Relationships (ER)

```
users 1───* receipts
users 1───* friendships (as requester / addressee)
receipts 1───* receipt_items
receipt_items 1───* item_assignments
receipts 1───* debts
debts 1───* debt_items
debts 1───* payments
users 1───* trust_score_events
users 1───* notifications
users 1───* refresh_tokens
```

---

## Security Design

- **Registration/Login** are the only public POST endpoints.
- **Password hashing**: `BCryptPasswordEncoder`.
- **Access token**: short-lived JWT (default 15 min), HMAC-SHA256 signed, subject = user id,
  claims include username. Stateless — validated by a `OncePerRequestFilter`.
- **Refresh token**: opaque random 256-bit token persisted in `refresh_tokens`, long-lived
  (default 14 days). Rotated on use; logout revokes it.
- **Authorization**: method/endpoint level. Ownership checks live in services (e.g., only the
  creditor can approve a payment) and throw `ForbiddenException` (→ 403).
- Secrets (JWT signing key, token TTLs) come from configuration/environment, not code.

### Auth flow

```
POST /api/auth/register  -> 201 (no tokens; user then logs in)
POST /api/auth/login     -> { accessToken, refreshToken }
POST /api/auth/refresh   -> { accessToken, refreshToken }  (rotates refresh)
POST /api/auth/logout    -> 204 (revokes refresh token)
```

---

## REST API Surface

All under `/api`. Protected unless noted. JSON unless multipart noted.

### Auth (public)
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`

### Profile
- `GET  /me` — full own profile with metrics
- `GET  /users/{username}` — public profile
- `POST /me/profile-image` (multipart) — upload avatar
- `GET  /users/search?query=` — search by username/email

### Friends
- `POST   /friends/requests` `{ addresseeUsername }`
- `GET    /friends/requests` — incoming pending
- `POST   /friends/requests/{id}/accept`
- `POST   /friends/requests/{id}/reject`
- `GET    /friends` — accepted friends
- `DELETE /friends/{userId}` — remove friend

### Receipts & items
- `POST   /receipts` (multipart: metadata + image)
- `GET    /receipts/{id}`
- `GET    /receipts/{id}/image` — streams stored image
- `POST   /receipts/{id}/items` `{ name, quantity, unitPrice }`
- `PUT    /receipts/{id}/items/{itemId}`
- `DELETE /receipts/{id}/items/{itemId}`
- `GET    /receipts/{id}/items`

### Assignments & debt generation
- `POST /receipts/{id}/items/{itemId}/assignments` `{ splitType, assigneeUsernames[] }`
- `DELETE /receipts/{id}/items/{itemId}/assignments/{assignmentId}`
- `POST /receipts/{id}/finalize` — generates debts, returns created debts

### Debts
- `GET /debts/dashboard` — sections + summary metrics
- `GET /debts/{id}` — debt detail
- `GET /debts/{id}/explanation` — the "Why do I owe this?" payload
- `GET /debts/{id}/history` — lifecycle timeline

### Payments
- `POST /debts/{id}/payments` `{ amount, method, notes }`
- `GET  /debts/{id}/payments`
- `POST /payments/{id}/approve`
- `POST /payments/{id}/reject`

### Notifications
- `GET  /notifications?unread=`
- `POST /notifications/{id}/read`

---

## Key Business Logic

### Equal-split remainder distribution (Req 6.3)

Given line total `T` (cents) and `N` assignees:
- base = floor(T / N), remainder = T mod N
- First `remainder` assignees get `base + 1` cent; the rest get `base`.
- Guarantees sum of shares == T exactly. Computed in integer cents, converted back to BigDecimal.

### Debt generation (Req 7)

On `finalize`:
1. Guard: receipt not already finalized, else 409.
2. Group all `item_assignments` by `assignee_id`, excluding the owner.
3. For each debtor: sum share amounts → `original_amount`; create one `debt`
   (creditor = receipt owner) with `outstanding_amount = original_amount`, status ACTIVE,
   `due_date = purchase_date + defaultTermDays`.
4. Snapshot each contributing item into `debt_items` (item name + amount) for the explanation.
5. Mark receipt finalized. Emit `DEBT_CREATED` notification to each debtor.
6. If no non-owner assignments, create no debts and return an empty list (Req 7.5).

### Payment approval & settlement (Req 11)

On approve:
1. Guard: caller is creditor (403 otherwise), payment is PENDING (409 otherwise).
2. `outstanding -= payment.amount`; if `outstanding == 0` → status SETTLED, `settled_at = now`.
3. Payment status APPROVED, `decided_at = now`.
4. Recalculate trust score for the debtor (see below).
5. Notify debtor: PAYMENT_APPROVED, and DEBT_SETTLED if newly settled.

### Trust score engine (Req 13)

- Start 500 on registration. Clamp to [300, 850].
- Triggered when a payment is approved and, for overdue, by a scheduled sweep.

| Event                              | Delta |
|------------------------------------|-------|
| Full settlement                    | +15   |
| Settled before due date (early)    | +10   |
| Settled on due date (on time)      | +5    |
| Settled after due date (late)      | -10   |
| Still unpaid 30+ days past due     | -25   |

- On settlement approval: apply full-settlement (+15) plus timing bonus/penalty based on
  `settled_at` vs `due_date`.
- Overdue penalty applied once per debt by a daily `@Scheduled` sweep that flags debts 30+ days
  past due that are still ACTIVE, using a `overdue_penalized` marker to avoid double-charging.
- Every change writes a `trust_score_events` row (delta + reason + resulting score) for audit
  and to compute metrics.

### Reputation level mapping (Req 2.5)

| Score range | Level     |
|-------------|-----------|
| < 450       | Poor      |
| 450–549     | Fair      |
| 550–649     | Good      |
| 650–749     | Very Good |
| ≥ 750       | Excellent |

### Profile metrics (Req 2)

- `debtsSettled` = count of debtor debts with status SETTLED.
- `currentDebts` = count of debtor debts with status ACTIVE.
- `averageRepaymentTime` = avg(days between debt.created_at and debt.settled_at) over settled
  debts; null when none.

---

## Error Handling

A `@RestControllerAdvice` maps exceptions to a consistent body:

```json
{ "timestamp": "...", "status": 409, "error": "Conflict", "message": "...", "path": "..." }
```

| Exception                | HTTP |
|--------------------------|------|
| ValidationException / bean validation | 400 |
| AuthenticationException   | 401 |
| ForbiddenException        | 403 |
| ResourceNotFoundException | 404 |
| ConflictException         | 409 |
| PayloadTooLargeException  | 413 |

Validation messages never leak whether username vs password was wrong on login (Req 1.5).

---

## Storage Design

- Images saved under configurable `storage.receipt-dir` (default `./storage/receipts`).
- Filename = `{uuid}.{ext}`; DB stores relative path + content type.
- On read, `GET .../image` streams the file with the stored content type after an ownership check.
- Allowed content types: `image/jpeg`, `image/png`, `application/pdf`. Max size configurable
  (default 10 MB) via Spring multipart limits → 413 on exceed.

---

## Testing Strategy

- **Unit**: equal-split remainder math, debt aggregation, trust-score deltas, reputation mapping.
- **Slice/Integration**: auth flow (register→login→access protected→refresh→logout), receipt
  upload + item + assignment + finalize → debt correctness, payment submit→approve→settle→score.
- **Money**: assert BigDecimal scale 2 and that split shares sum exactly to line totals.
- H2 in-memory for repository/web slices; Flyway/Postgres verified at least by app startup.
