# Cryptello Backend

Welcome to the backend repository for **Cryptello**, a modern crypto trading platform.

## Tech Stack
- **Framework**: Spring Boot 3+ (Java 17/21)
- **Database**: MySQL
- **Authentication**: JWT (Stateless)
- **Frontend**: React (Separate Repo)
- **Payments**: Cashfree (Primary), Stripe/Razorpay (Legacy/Backup)

## Project Structure
The project follows a domain-driven package structure:
- `com.cryptonex.auth` - Login, Signup, JWT handling
- `com.cryptonex.security` - Security configurations, Filters
- `com.cryptonex.user` - User profiles, Settings
- `com.cryptonex.trader` - Trader verification, Upgrades
- `com.cryptonex.payment` - Orders, Webhooks, Providers
- `com.cryptonex.feed` - Social feed (Posts, Signals)
- `com.cryptonex.common` - Shared utilities, Exceptions

## Configuration
The application uses profile-based configuration:
- `application-dev.properties`: For local development (H2/Local MySQL, React localhost).
- `application-prod.properties`: For production deployment (Env vars for secrets).

**Note**: Secrets (DB passwords, API keys) are **NOT** committed. They are loaded via Environment Variables.

## Security
See [SECURITY-MINIMUM.md](SECURITY-MINIMUM.md) for the security baseline.

## Running Locally
```bash
# Run with Dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
