# ReceiptTrust MVP — Requirements

## Introduction

ReceiptTrust is a receipt-backed social debt and trust platform. Users upload receipts,
manually enter items, assign those items to friends, and the system automatically generates
debts. Debtors repay, creditors approve, and a trust score reflects repayment behavior.

The MVP is a **backend-only REST API** built with Java 21, Spring Boot 3.x, Spring Security
with JWT (access + refresh tokens), and PostgreSQL. Receipt images are stored on the local
filesystem. The MVP explicitly excludes OCR, AI, leaderboards, badges, subscriptions, mobile
apps, push/email notifications, advanced analytics, and payment gateway integration.

### Glossary

- **Creditor**: the user who paid for a receipt and is owed money.
- **Debtor**: a user assigned items on a receipt and therefore owes money.
- **Debt**: an obligation from one debtor to one creditor, backed by a receipt and assigned items.
- **Outstanding amount**: remaining unpaid balance on a debt.
- **Trust score**: an integer starting at 500 that changes with repayment behavior.

---

## Requirement 1 — Authentication

**User Story:** As a user, I want to register and log in securely, so that I can access the
platform and my data is protected.

#### Acceptance Criteria

1. WHEN a user submits full name, username, email, and password THEN the system SHALL create an account with the password stored as a BCrypt hash.
2. IF the submitted username or email already exists THEN the system SHALL reject registration with HTTP 409 and SHALL NOT create a duplicate account.
3. IF the email format is invalid OR the password is shorter than 8 characters OR any required field is missing THEN the system SHALL reject registration with HTTP 400 and a validation message.
4. WHEN a user submits valid credentials to the login endpoint THEN the system SHALL return a signed JWT access token and a refresh token.
5. IF login credentials are invalid THEN the system SHALL respond with HTTP 401 and SHALL NOT reveal whether the username or the password was wrong.
6. WHEN a request includes a valid, unexpired access token THEN the system SHALL authorize access to protected endpoints.
7. IF an access token is missing, malformed, or expired THEN the system SHALL respond with HTTP 401.
8. WHEN a user submits a valid refresh token THEN the system SHALL issue a new access token.
9. IF a refresh token is expired, revoked, or unknown THEN the system SHALL respond with HTTP 401.
10. WHEN a user logs out THEN the system SHALL revoke the associated refresh token so it can no longer be used.

---

## Requirement 2 — User Profile

**User Story:** As a user, I want a profile showing my identity and trust metrics, so that I
and others can gauge repayment reputation.

#### Acceptance Criteria

1. WHEN an authenticated user requests their own profile THEN the system SHALL return name, username, email, optional profile image reference, trust score, debts settled count, current (active) debt count, and average repayment time in days.
2. WHEN a user requests another user's public profile THEN the system SHALL return name, username, trust score, debts settled, average repayment time, and reputation level, but SHALL NOT return the other user's email or private debt details.
3. WHEN a user has settled zero debts THEN the system SHALL report average repayment time as null or 0 rather than an error.
4. WHEN a user uploads a profile image THEN the system SHALL store it and associate its reference with the profile.
5. WHERE a trust score maps to a reputation level THEN the system SHALL derive the level from the score using defined thresholds.

---

## Requirement 3 — Friend Management

**User Story:** As a user, I want to find and connect with other users, so that I can share
debts with them.

#### Acceptance Criteria

1. WHEN a user searches by username or email THEN the system SHALL return matching users excluding the requester.
2. WHEN a user sends a friend request to another user THEN the system SHALL create a relationship with status PENDING.
3. IF a pending or accepted relationship already exists between the two users THEN the system SHALL reject a new friend request with HTTP 409.
4. IF a user sends a friend request to themselves THEN the system SHALL reject it with HTTP 400.
5. WHEN the recipient accepts a pending request THEN the system SHALL set the relationship status to ACCEPTED.
6. WHEN the recipient rejects a pending request THEN the system SHALL set the relationship status to REJECTED.
7. IF a user who is not the recipient attempts to accept or reject a request THEN the system SHALL respond with HTTP 403.
8. WHEN a user removes an accepted friend THEN the system SHALL delete the friendship relationship.
9. WHEN a user lists their friends THEN the system SHALL return only users with ACCEPTED relationships.

---

## Requirement 4 — Receipt Upload

**User Story:** As a user, I want to upload a receipt image with details, so that my debts are
backed by proof.

#### Acceptance Criteria

1. WHEN a user uploads a receipt with store name, purchase date, image file, and optional notes THEN the system SHALL persist the receipt owned by that user and store the image on the local filesystem.
2. IF the uploaded file is not JPG, PNG, or PDF THEN the system SHALL reject it with HTTP 400.
3. IF the uploaded file exceeds the configured maximum size THEN the system SHALL reject it with HTTP 413.
4. IF store name or purchase date is missing THEN the system SHALL reject the upload with HTTP 400.
5. WHEN a user requests a receipt they own or are a debtor on THEN the system SHALL return the receipt metadata and a way to retrieve the image.
6. IF a user requests a receipt they neither own nor are a debtor on THEN the system SHALL respond with HTTP 403.

---

## Requirement 5 — Receipt Item Management

**User Story:** As a receipt owner, I want to add itemized entries, so that costs can be
assigned precisely.

#### Acceptance Criteria

1. WHEN the receipt owner adds an item with name, quantity, and unit price THEN the system SHALL persist the item linked to the receipt.
2. IF quantity is less than 1 OR price is negative THEN the system SHALL reject the item with HTTP 400.
3. WHEN the owner updates or deletes an item AND no debts have been generated for the receipt THEN the system SHALL apply the change.
4. IF debts have already been generated for the receipt THEN the system SHALL reject item modification with HTTP 409.
5. WHEN items are listed for a receipt THEN the system SHALL return each item's line total (quantity × unit price).

---

## Requirement 6 — Item Assignment

**User Story:** As a receipt owner, I want to assign items to friends individually or as an
equal split, so that responsibility is clear.

#### Acceptance Criteria

1. WHEN the owner assigns an item entirely to one friend THEN the system SHALL record that friend as responsible for the item's full line total.
2. WHEN the owner assigns an item as an equal split among N friends THEN the system SHALL divide the item's line total equally into N shares.
3. IF an equal split does not divide evenly THEN the system SHALL distribute the remainder in cents deterministically so the sum of shares equals the line total exactly.
4. IF an assignee is not an accepted friend of the owner THEN the system SHALL reject the assignment with HTTP 400.
5. IF the same item is assigned more than 100% in total THEN the system SHALL reject the assignment with HTTP 400.
6. WHEN the owner assigns an item to themselves THEN the system SHALL exclude the owner's share from generated debts.

---

## Requirement 7 — Automatic Debt Generation

**User Story:** As a receipt owner, I want debts generated automatically from assignments, so
that I don't compute balances manually.

#### Acceptance Criteria

1. WHEN the owner finalizes a receipt's assignments THEN the system SHALL create one debt per debtor aggregating that debtor's shares across all items on the receipt.
2. WHEN a debt is created THEN the system SHALL record debt id, creditor, debtor, purchase date, receipt id, assigned items, original amount, outstanding amount, and status ACTIVE.
3. WHEN a debt is created THEN the system SHALL set outstanding amount equal to original amount.
4. IF a receipt has already been finalized THEN the system SHALL reject a second finalize attempt with HTTP 409.
5. IF a receipt has no assignments to anyone other than the owner THEN finalizing SHALL produce no debts and SHALL report that outcome without error.

---

## Requirement 8 — Debt Dashboard

**User Story:** As a user, I want a dashboard of what I owe and what I am owed, so that I
understand my position at a glance.

#### Acceptance Criteria

1. WHEN a user requests their dashboard THEN the system SHALL return debts owed to them grouped by debtor and debts they owe grouped by creditor.
2. WHEN a user requests their dashboard THEN the system SHALL return summary metrics: total owed to me, total I owe, active debt count, and settled debt count.
3. WHERE a debt is fully repaid THEN the system SHALL classify it as SETTLED and exclude it from active totals.

---

## Requirement 9 — Debt Explanation

**User Story:** As a debtor, I want to see exactly why I owe a debt, so that disputes and
confusion are reduced.

#### Acceptance Criteria

1. WHEN a debtor opens the explanation for a debt THEN the system SHALL return who paid, store name, purchase date, the specific items and amounts assigned to that debtor, a reference to the receipt image, and the total debt amount.
2. IF a user who is neither the debtor nor the creditor requests the explanation THEN the system SHALL respond with HTTP 403.
3. WHEN the explanation is returned THEN the item amounts listed SHALL sum to the debt's original amount.

---

## Requirement 10 — Payment Tracking

**User Story:** As a debtor, I want to record a repayment, so that my creditor can review it.

#### Acceptance Criteria

1. WHEN a debtor submits a payment with amount, payment method, and optional notes against a debt THEN the system SHALL record the payment with status PENDING.
2. IF the payment amount is less than or equal to zero OR greater than the debt's outstanding amount THEN the system SHALL reject it with HTTP 400.
3. IF the payment method is not one of CASH, BANK_TRANSFER, GCASH, MAYA, OTHER THEN the system SHALL reject it with HTTP 400.
4. IF a user who is not the debtor submits a payment THEN the system SHALL respond with HTTP 403.
5. IF the debt is already SETTLED THEN the system SHALL reject the payment with HTTP 409.

---

## Requirement 11 — Payment Approval

**User Story:** As a creditor, I want to approve or reject submitted payments, so that
settlements can't be faked.

#### Acceptance Criteria

1. WHEN the creditor approves a pending payment THEN the system SHALL set the payment status to APPROVED and SHALL reduce the debt's outstanding amount by the payment amount.
2. WHEN an approval reduces outstanding amount to zero THEN the system SHALL set the debt status to SETTLED and record the settlement timestamp.
3. WHEN the creditor rejects a pending payment THEN the system SHALL set the payment status to REJECTED and SHALL NOT change the outstanding amount.
4. IF a user who is not the creditor attempts to approve or reject THEN the system SHALL respond with HTTP 403.
5. IF the payment is not in PENDING status THEN the system SHALL reject the approve/reject action with HTTP 409.
6. WHEN a payment is approved or rejected THEN the system SHALL trigger trust score recalculation for the debtor.

---

## Requirement 12 — Payment History

**User Story:** As a participant in a debt, I want a chronological history, so that I can track
the full lifecycle.

#### Acceptance Criteria

1. WHEN a debtor or creditor requests a debt's history THEN the system SHALL return timeline events: receipt uploaded, debt created, each payment submitted, each payment approved or rejected, and debt settled.
2. WHEN listing approved payments THEN the system SHALL show amount paid, payment date, and the remaining balance after that payment.
3. IF a user unrelated to the debt requests the history THEN the system SHALL respond with HTTP 403.

---

## Requirement 13 — Trust Score System

**User Story:** As a user, I want a trust score that reflects my repayment behavior, so that my
financial reputation is visible.

#### Acceptance Criteria

1. WHEN a user account is created THEN the system SHALL initialize the trust score to 500.
2. WHEN an approved payment fully settles a debt before the due date THEN the system SHALL add points for early payment and full settlement.
3. WHEN an approved payment settles a debt on the due date THEN the system SHALL add on-time points.
4. WHEN a debt is settled after the due date THEN the system SHALL subtract late-payment points.
5. WHEN a debt remains unpaid for 30 or more days past its due date THEN the system SHALL subtract overdue points.
6. WHERE the score is applied THEN the system SHALL clamp it to a defined range (e.g., 300–850) so it never goes below or above bounds.
7. WHEN the score changes THEN the system SHALL record an audit entry with the reason and delta.

---

## Requirement 14 — In-App Notifications

**User Story:** As a user, I want in-app notifications for key events, so that I stay engaged
without email or push.

#### Acceptance Criteria

1. WHEN a friend request is received, a friend request is accepted, a debt is created, a payment is submitted, a payment is approved, or a debt is settled THEN the system SHALL persist a notification for the affected user with a type and message.
2. WHEN a user lists notifications THEN the system SHALL return them newest first with read/unread state.
3. WHEN a user marks a notification as read THEN the system SHALL update its state to read.
4. IF a user requests another user's notifications THEN the system SHALL respond with HTTP 403.

---

## Non-Functional Requirements

1. All state-changing endpoints except registration and login SHALL require a valid JWT.
2. Passwords SHALL never be returned in any API response.
3. Monetary values SHALL be stored and computed with exact decimal precision (no floating-point rounding errors).
4. All list endpoints returning potentially large collections SHALL support pagination.
5. The application SHALL run against PostgreSQL and start with schema migrations applied automatically.
