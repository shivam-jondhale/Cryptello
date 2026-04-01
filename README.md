# Cryptello Backend

Welcome to the backend repository for **Cryptello**, a comprehensive and modern crypto trading platform.

## What is Cryptello?
Cryptello is an advanced cryptocurrency trading application designed to connect everyday users with experienced "pro" traders. The platform allows users to:
- **Track Cryptocurrencies**: Get real-time data, price feeds, and tracking for various crypto assets.
- **Trade and Invest**: Seamlessly manage wallets, process transactions, and oversee portfolio growth.
- **Follow Traders (Signals)**: Engage in social trading by following expert traders, viewing their trade signals, and subscribing to their premium feeds for exclusive insights.
- **Secure Transactions**: Process secure payments using integrated providers (Cashfree, Stripe, Razorpay) for wallet deposits, withdrawals, and subscription fees.
- **Intelligent Assistant**: Interact with an AI-powered chatbot to get quick market insights, platform help, and crypto updates.

This repository houses the robust Spring Boot REST API that powers all these features, providing a secure, stateless, and highly available engine for frontend clients.

## Tech Stack
- **Framework**: Spring Boot 3+ (Java 17/21)
- **Database**: MySQL
- **Authentication**: JWT (Stateless OAuth2 + Email/Password)
- **Frontend**: React (Separate Repository)
- **Payments**: Cashfree (Primary), Stripe/Razorpay (Legacy/Backup)
- **External APIs**: CoinGecko, CoinMarketCap, Gemini (AI Chatbot)

## Project Structure
The project follows a domain-driven architectural package structure:
- `com.cryptello.auth` - Login, Signup, JWT provision, and 2FA handling
- `com.cryptello.security` - Advanced Security configurations, Rate Limiting, CORS Filters
- `com.cryptello.user` - User profiles, Watchlists, Settings
- `com.cryptello.trader` - Trader verification and account upgrades
- `com.cryptello.payment` - Payment Orders, Webhooks, Wallet Transactions
- `com.cryptello.feed` - Social feed, Posts, and Trade Signals
- `com.cryptello.common` - Shared utilities and global exception handlers

## Configuration
The application gracefully uses role-based and profile-based configurations:
- `application-dev.properties`: For local development (Local Database).
- `application-prod.properties`: For production deployment (Env vars for API secrets and production databases).

**Note:** All sensitive credentials (DB passwords, API keys) are **NOT** committed to this repository. They are dynamically loaded via Environment Variables during startup for strict security.

## Security Overview
- Stateless JWT authentication is used across the system.
- Includes rate-limiting (to prevent DoS) and account lockout policies for brute-force attack prevention.
- Please refer to [SECURITY-MINIMUM.md](SECURITY-MINIMUM.md) for the complete security baseline.

## Running Locally

To run the application locally on your machine, simply execute:
```bash
# Run with Dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
