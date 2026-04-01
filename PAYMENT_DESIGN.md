# Payment Design Document

## 1. Domain Model

### 1.1 PaymentOrder
Represents a user's intent to pay.
- **Fields**: `id`, `userId`, `amount`, `currency`, `status` (PENDING, SUCCESS, FAILED), `paymentLinkId`, `provider` (CASHFREE, STRIPE).
- **Lifecycle**:
    1.  Created (PENDING) -> User redirected to Provider.
    2.  Webhook Received (SUCCESS/FAILED) -> Status updated.

### 1.2 SubscriptionPlan
Defines the product being sold.
- **Fields**: `id`, `name`, `price`, `durationMonths`, `planType` (PLATFORM, TRADER).

### 1.3 Subscription
Tracks the user's active access.
- **Fields**: `id`, `userId`, `planId`, `startDate`, `endDate`, `status` (ACTIVE, EXPIRED).
- **Logic**: Extended upon successful `PaymentOrder`.

## 2. Provider Strategy

### 2.1 Primary: Cashfree (Sandbox)
- **Flow**:
    1.  Create Payment Link via API.
    2.  Redirect user to `paymentLink`.
    3.  Listen for Webhook (`PAYMENT_SUCCESS`).
    4.  Verify Signature (`x-webhook-signature`).

### 2.2 Secondary: Stripe (Test Mode)
- **Flow**:
    1.  Create Checkout Session.
    2.  Redirect user to `url`.
    3.  Listen for Webhook (`checkout.session.completed`).
    4.  Verify Signature (`Stripe-Signature`).

## 3. Webhook Handling

### 3.1 Security
- **Signature Verification**: MANDATORY. Requests without valid signatures must be rejected (400/401).
- **Rate Limiting**: Trusted IPs (if known) or high limits for signed requests.

### 3.2 Idempotency
- **Duplicate Events**: Webhooks may be delivered multiple times.
- **Handling**: Check if `PaymentOrder` is already processed. If yes, return 200 OK immediately without side effects.

### 3.3 Resilience
- **Late Webhooks**: System must accept webhooks even if the user has closed the browser.
- **Reconciliation**: Periodic job (future phase) to check status of PENDING orders.
