# ReceiptTrust MVP — Implementation Plan

- [x] 1. Scaffold the Spring Boot project
  - Maven `pom.xml` (Java 21, Spring Boot 3.3.x): web, security, data-jpa, validation,
    postgresql, flyway, jjwt, H2 (test), spring-boot-starter-test.
  - `application.yml` (datasource, JPA, multipart limits, storage dir, JWT settings).
  - Main application class; base package `com.receipttrust`.
  - _Requirements: NFR 5_

- [x] 2. Common foundation
  - Base auditable entity (`created_at`, `updated_at`).
  - Exception types + `@RestControllerAdvice` global handler + error DTO.
  - _Requirements: NFR 4, Error handling_

- [x] 3. Domain entities and repositories
  - JPA entities for all tables in design.md; enums for statuses/methods.
  - Spring Data repositories with the queries needed for metrics and dashboard.
  - Flyway migration `V1__init.sql` creating the schema.
  - _Requirements: 1–14 data model_

- [x] 4. Security + authentication (Feature 1)
  - `BCryptPasswordEncoder`, JWT token service (access), refresh-token service (persist/rotate/revoke).
  - `JwtAuthenticationFilter`, `SecurityFilterChain` (public: register/login/refresh).
  - `AuthController`: register, login, refresh, logout. DTOs + validation.
  - _Requirements: 1.1–1.10, NFR 1, NFR 2_

- [x] 5. User profile (Feature 2)
  - `/me`, `/users/{username}`, search, profile-image upload.
  - Metrics service (debts settled, current debts, avg repayment time, reputation level).
  - _Requirements: 2.1–2.5_

- [x] 6. Friend management (Feature 3)
  - Requests (send/accept/reject), list friends, remove friend, search.
  - Guards for self-request, duplicates, non-recipient actions.
  - Notifications: FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED.
  - _Requirements: 3.1–3.9, 14.1_

- [x] 7. Receipt upload + item management (Features 4, 5)
  - Multipart receipt create + local storage; image streaming with ownership check.
  - Item CRUD with validation; block edits after finalize.
  - _Requirements: 4.1–4.6, 5.1–5.5_

- [x] 8. Item assignment + debt generation (Features 6, 7)
  - Assignment endpoints (INDIVIDUAL, EQUAL) with friend/over-assignment guards.
  - Equal-split remainder distribution in integer cents.
  - `finalize`: aggregate per debtor, create debts + debt_items snapshot, notify debtors.
  - _Requirements: 6.1–6.6, 7.1–7.5, 14.1_

- [x] 9. Debt dashboard + explanation + history (Features 8, 9, 12)
  - Dashboard sections + summary metrics.
  - Explanation payload with debtor's items + receipt image reference.
  - History timeline builder.
  - _Requirements: 8.1–8.3, 9.1–9.3, 12.1–12.3_

- [x] 10. Payments: tracking + approval (Features 10, 11)
  - Submit payment (validation, ownership, outstanding checks).
  - Approve/reject (creditor-only, PENDING-only), settle debt, notify.
  - Trigger trust recalculation on decision.
  - _Requirements: 10.1–10.5, 11.1–11.6, 14.1_

- [x] 11. Trust score engine (Feature 13)
  - Score deltas on settlement (full + timing), clamp [300,850], audit events.
  - Scheduled overdue sweep (30+ days) with single-charge marker.
  - _Requirements: 13.1–13.7_

- [x] 12. Notifications (Feature 14)
  - List (newest first, unread filter), mark read, ownership guard.
  - Event producers persist notifications.
  - _Requirements: 14.1–14.4_

- [x] 13. Verification
  - Unit tests: split math, reputation mapping.
  - Integration tests: full lifecycle (auth → receipt → assign → finalize → payment →
    approve → settle → trust score) plus Spring context load.
  - `mvn verify` green; app starts with Flyway on PostgreSQL.
  - _Requirements: Testing strategy, all_
