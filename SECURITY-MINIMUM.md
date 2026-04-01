# Security Minimum

> [!WARNING]
> **Razorpay integration is temporarily disabled.**
> Cashfree sandbox is the primary active provider, with Stripe as a secondary provider for selected plans.
> Razorpay endpoints must not be exposed or used in Cryptello v1.

This document defines the baseline security requirements for the Cryptello backend.

## 1. Transport Security
- [ ] **HTTPS Required**: All production traffic must use HTTPS (TLS 1.2+).
- [ ] **HSTS**: HTTP Strict Transport Security enabled in production (max-age=31536000).
- [ ] **CORS**:
    - Allowed Origins: `http://localhost:5173` (Dev), `https://cryptello.com` (Prod).
    - Methods: GET, POST, PUT, DELETE, OPTIONS.
    - Headers: Authorization, Content-Type.

## 2. API Security
- [ ] **Endpoint Protection**: All endpoints under `/api/**` must be authenticated, except:
    - `/api/auth/**` (Login/Signup)
    - `/api/public/**` (Health checks, Public data)
    - `/api/webhooks/**` (Payment callbacks - secured via Signature Verification)
- [ ] **Rate Limiting**:
    - **Auth**: 5 req/min (Signup), 30 req/min (Signin).
    - **Payments**: 10 req/min (User).
    - **Webhooks**: 200 req/min (Signed), 10 req/min (Unsigned).

## 3. Authentication & Authorization
- [ ] **Stateless Auth**: JWT (JSON Web Tokens) used for all user sessions.
    - **Token Lifetime**: 24 hours (Access Token). No Refresh Token (v1).
- [ ] **Password Policy**:
    - Min length: 8 characters.
    - Hashing: BCrypt (Strength 10+).
- [ ] **Role Hierarchy**:
    - `ROLE_USER`: Basic access (View coins, Watchlist).
    - `ROLE_TRADER`: Can execute trades (Requires Subscription).
    - `ROLE_ADMIN`: System management (Manage Users, Plans).
- [ ] **No Sessions**: Server-side sessions are disabled.

## 4. Data Protection
- [ ] **Secrets**: No secrets (API keys, Passwords) in source code. Use Environment Variables.
    - `JWT_SECRET`
    - `DB_PASSWORD`
    - `CASHFREE_SECRET_KEY`
    - `STRIPE_SECRET_KEY`

## 5. Payments
- [ ] **Trust**: Payment status updates trusted **ONLY** via verified Webhook signatures.
- [ ] **Idempotency**: Webhooks must handle duplicate events safely.
