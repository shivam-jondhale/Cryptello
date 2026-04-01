# Project Issues & Milestones

This document tracks the planned work for Cryptello Backend, organized by Phase.

## Phase 1: Security & Role Structure (Area: Security)
- [ ] **Issue #101**: Implement Role Hierarchy (`USER`, `TRADER`, `ADMIN`).
- [ ] **Issue #102**: Secure all API endpoints (deny by default).
- [ ] **Issue #103**: Configure CORS for Dev/Prod.

## Phase 2: Auth Pipeline (Area: Auth)
- [ ] **Issue #201**: Implement JWT Authentication Filter.
- [ ] **Issue #202**: Implement Login/Signup endpoints.
- [ ] **Issue #203**: Add Password Hashing (BCrypt).

## Phase 3: Payments (Area: Payments)
- [ ] **Issue #301**: Implement `PaymentOrder` and `Subscription` entities.
- [ ] **Issue #302**: Integrate Cashfree (Sandbox).
- [ ] **Issue #303**: Integrate Stripe (Test Mode).
- [ ] **Issue #304**: Implement Webhook handling with Signature Verification.
- [ ] **Issue #305**: Implement Idempotency for webhooks.

## Phase 4: Worker & Jobs (Area: Worker)
- [ ] **Issue #401**: Setup CoinGecko Scheduler.
- [ ] **Issue #402**: Implement Payment Reconciliation Job.

## Phase 5: Observability (Area: Observability)
- [ ] **Issue #501**: Add Actuator Endpoints.
- [ ] **Issue #502**: Configure Prometheus Metrics.

## Phase 6: Rate Limiting (Area: Rate-Limiting)
- [ ] **Issue #601**: Implement Bucket4j Rate Limiting.
- [ ] **Issue #602**: Configure per-IP and per-User limits.

## Phase 7: Trading Engine (Area: Trading)
- [ ] **Issue #701**: Implement Buy/Sell Order logic.
- [ ] **Issue #702**: Implement Wallet management.

## Phase 8: Deployment (Area: DevOps)
- [ ] **Issue #801**: Dockerize Application.
- [ ] **Issue #802**: Setup CI/CD Pipeline.

## Phase 9: Final Polish (Area: General)
- [ ] **Issue #901**: Comprehensive Code Review.
- [ ] **Issue #902**: Final Security Audit.
